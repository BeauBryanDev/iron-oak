package com.ironoak.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.TestOrders;
import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quote requests for QUOTE-priced services: request, staff price or decline, pay by order number. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ServiceQuoteFlowTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final String EMAIL = "shop@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AdminUserRepository adminUsers;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    private String bearer;

    @BeforeEach
    void admin() {
        if (adminUsers.findByUsername("quoter").isEmpty()) {
            adminUsers.save(new AdminUser("quoter", "quoter@ironoak.test",
                    passwordEncoder.encode("correct-horse-battery"), "Quoter"));
        }
        bearer = "Bearer " + jwtService.issueToken("quoter");
    }

    private JsonNode send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request, String body) {
        return json(request.header("Authorization", bearer), body);
    }

    private long requestQuote(String serviceCode, int expectedStatus) throws Exception {
        JsonNode quote = send(json(post("/api/service-quotes"), """
                {"serviceCode":"%s","customerName":"Ada Machinist","customerEmail":"%s",
                 "country":"co","city":"Bogota","description":"Spindle runout 0.05 mm on a VF-2, needs rebuild"}
                """.formatted(serviceCode, EMAIL)), expectedStatus);
        return quote == null || !quote.has("id") ? -1 : quote.get("id").asLong();
    }

    @Test
    void staffPriceARequestAndTheCustomerPaysItByOrderNumber() throws Exception {
        long id = requestQuote("SPINDLE_TOOLING_SERVICE", 201);

        JsonNode mine = send(get("/api/service-quotes/" + id).param("email", "SHOP@example.com"), 200);
        assertThat(mine.get("status").asText()).isEqualTo("REQUESTED");
        assertThat(mine.get("country").asText()).isEqualTo("CO");
        send(get("/api/service-quotes/" + id).param("email", "someone@else.com"), 404);

        send(json(post("/api/admin/service-quotes/" + id + "/quote"), "{\"price\":450}"), 401);
        JsonNode quoted = send(asAdmin(post("/api/admin/service-quotes/" + id + "/quote"),
                "{\"price\":450,\"note\":\"Includes bearings and runout check\"}"), 200);
        assertThat(quoted.get("status").asText()).isEqualTo("QUOTED");
        assertThat(quoted.get("orderStatus").asText()).isEqualTo("PENDING_PAYMENT");
        // 450 + 19% Colombian tax (items above 100 USD), nothing to ship
        assertThat(quoted.get("grandTotal").decimalValue()).isEqualByComparingTo("535.50");

        String number = quoted.get("orderNumber").asText();
        JsonNode order = send(get("/api/orders/" + number), 200);
        assertThat(order.get("payable").asBoolean()).isTrue();
        assertThat(order.get("grandTotal").decimalValue()).isEqualByComparingTo("535.50");

        // priced only once, and the price is in the audit trail
        send(asAdmin(post("/api/admin/service-quotes/" + id + "/quote"), "{\"price\":500}"), 422);
        assertThat(jdbc.queryForObject("select count(*) from admin_audit_log where action = 'SERVICE_QUOTE_PRICED'"
                + " and resource_id = ?", Integer.class, String.valueOf(id))).isEqualTo(1);
    }

    @Test
    void onlyQuoteServicesTakeRequestsAndADeclineNeedsANote() throws Exception {
        long quoteService = jdbc.queryForObject(
                "select id from service_offering where code = 'SPINDLE_TOOLING_SERVICE'", Long.class);
        send(json(post("/api/orders"), TestOrders.webJson("Ada", EMAIL, "SERVICE", quoteService, 1)), 400);
        requestQuote("PREVENTIVE_MAINTENANCE", 422); // has a listed price: order it directly
        requestQuote("NO_SUCH_SERVICE", 404);

        long id = requestQuote("SPINDLE_TOOLING_SERVICE", 201);
        send(asAdmin(post("/api/admin/service-quotes/" + id + "/decline"), "{\"note\":\" \"}"), 400);
        JsonNode declined = send(asAdmin(post("/api/admin/service-quotes/" + id + "/decline"),
                "{\"note\":\"We do not service that spindle model\"}"), 200);
        assertThat(declined.get("status").asText()).isEqualTo("DECLINED");
        assertThat(declined.get("orderNumber").isNull()).isTrue();
        send(asAdmin(post("/api/admin/service-quotes/" + id + "/quote"), "{\"price\":450}"), 422);

        JsonNode open = send(get("/api/admin/service-quotes").param("status", "DECLINED")
                .header("Authorization", bearer), 200);
        assertThat(open.get("content")).extracting(n -> n.get("id").asLong()).contains(id);
    }
}
