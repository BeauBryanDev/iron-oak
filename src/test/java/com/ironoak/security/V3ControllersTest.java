package com.ironoak.security;

import com.ironoak.TestOrders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.repository.ProductRepository;
import com.ironoak.repository.ServiceOfferingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP behaviour of the e-commerce controllers: public vs staff routes, ownership, error codes. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class V3ControllersTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

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
    private ProductRepository products;
    @Autowired
    private ServiceOfferingRepository serviceOfferings;

    private String bearer;

    @BeforeEach
    void admin() {
        adminUsers.deleteAll();
        adminUsers.save(new AdminUser("owner", "owner@ironoak.test",
                passwordEncoder.encode("correct-horse"), "Store Owner"));
        bearer = "Bearer " + jwtService.issueToken("owner");
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private String send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer);
    }

    private String checkoutBody(String email) {
        return TestOrders.webJson("Jane Doe", email, "PRODUCT",
                products.findBySku("IO-AICO-001").orElseThrow().getId(), 1);
    }

    private long placeCompletedOrder(String email) throws Exception {
        long id = json(send(asJson(post("/api/orders"), checkoutBody(email)), 201)).get("id").asLong();
        // staff record the payment, which confirms the order
        long paymentId = json(send(asJson(asAdmin(post("/api/admin/payments")),
                "{\"orderId\":" + id + ",\"provider\":\"manual\",\"providerReference\":\"ref-" + id + "\"}"), 201)).get("id").asLong();
        send(asJson(asAdmin(patch("/api/admin/payments/" + paymentId + "/status")), "{\"status\":\"PAID\"}"), 200);
        send(asJson(asAdmin(patch("/api/admin/orders/" + id + "/status")), "{\"status\":\"IN_PROGRESS\"}"), 200);
        send(asJson(asAdmin(patch("/api/admin/orders/" + id + "/status")), "{\"status\":\"COMPLETED\"}"), 200);
        return id;
    }

    @Test
    void checkoutIsIdempotentAndCustomersReadTheirOwnOrder() throws Exception {
        String email = "idem@example.com";
        JsonNode first = json(send(asJson(post("/api/orders").header("Idempotency-Key", "k-1"), checkoutBody(email)), 201));
        JsonNode again = json(send(asJson(post("/api/orders").header("Idempotency-Key", "k-1"), checkoutBody(email)), 201));
        assertThat(again.get("id").asLong()).isEqualTo(first.get("id").asLong());

        send(get("/api/orders/" + first.get("id").asLong()).param("email", "IDEM@example.com"), 200);
        send(get("/api/orders/" + first.get("id").asLong()).param("email", "other@example.com"), 404);
        send(get("/api/orders/" + first.get("id").asLong()), 400);
    }

    @Test
    void corsAllowsTheIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(options("/api/orders")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "idempotency-key,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void bookingFlow() throws Exception {
        String email = "booker@example.com";
        String when = OffsetDateTime.now().plusDays(4).toString();
        String body = "{\"customerName\":\"Bo Ker\",\"customerEmail\":\"" + email + "\",\"serviceOfferingId\":"
                + serviceOfferings.findByCode("PREVENTIVE_MAINTENANCE").orElseThrow().getId()
                + ",\"locationAddress\":\"Plant 1\",\"scheduledAt\":\"" + when + "\"}";
        long id = json(send(asJson(post("/api/bookings"), body), 201)).get("id").asLong();

        send(get("/api/bookings/" + id).param("email", email), 200);
        send(get("/api/bookings/" + id).param("email", "stranger@example.com"), 404);
        mockMvc.perform(get("/api/bookings").param("email", email))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        send(asJson(post("/api/bookings"), body.replace(when, OffsetDateTime.now().minusDays(1).toString())), 400);

        send(asJson(post("/api/bookings/" + id + "/reschedule").param("email", email),
                "{\"scheduledAt\":\"" + OffsetDateTime.now().plusDays(9) + "\"}"), 200);

        send(get("/api/admin/bookings"), 401);
        mockMvc.perform(asAdmin(get("/api/admin/bookings")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());
        send(asJson(asAdmin(patch("/api/admin/bookings/" + id + "/status")), "{\"status\":\"COMPLETED\"}"), 422);

        mockMvc.perform(post("/api/bookings/" + id + "/cancel").param("email", email))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        send(post("/api/bookings/" + id + "/cancel").param("email", email), 422);
    }

    @Test
    void warrantyClaimFlow() throws Exception {
        String email = "claimant@example.com";
        long orderId = placeCompletedOrder(email);
        long itemId = json(send(asAdmin(get("/api/admin/orders/" + orderId)), 200)).get("items").get(0).get("id").asLong();
        String body = "{\"customerEmail\":\"" + email + "\",\"orderId\":" + orderId + ",\"orderItemId\":" + itemId
                + ",\"description\":\"Motor died\"}";

        long claimId = json(send(asJson(post("/api/warranty-claims"), body), 201)).get("id").asLong();
        send(asJson(post("/api/warranty-claims"), body), 422);
        send(asJson(post("/api/warranty-claims"), body.replace(email, "stranger@example.com")), 404);
        send(get("/api/warranty-claims/" + claimId).param("email", email), 200);
        send(get("/api/warranty-claims/" + claimId).param("email", "stranger@example.com"), 404);

        send(get("/api/admin/warranty-claims"), 401);
        send(asJson(asAdmin(patch("/api/admin/warranty-claims/" + claimId + "/status")), "{\"status\":\"IN_REVIEW\"}"), 200);
        send(asJson(asAdmin(patch("/api/admin/warranty-claims/" + claimId + "/status")), "{\"status\":\"APPROVED\"}"), 422);
        mockMvc.perform(asJson(asAdmin(patch("/api/admin/warranty-claims/" + claimId + "/status")),
                        "{\"status\":\"APPROVED\",\"resolutionNote\":\"Replace the unit\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void paymentsAreStaffOnlyAndDeriveTheAmount() throws Exception {
        long orderId = json(send(asJson(post("/api/orders"), checkoutBody("payer@example.com")), 201)).get("id").asLong();
        String body = "{\"orderId\":" + orderId + ",\"provider\":\"stripe\",\"providerReference\":\"pi_http_1\"}";

        send(asJson(post("/api/admin/payments"), body), 401);
        JsonNode payment = json(send(asJson(asAdmin(post("/api/admin/payments")), body), 201));
        assertThat(payment.get("status").asText()).isEqualTo("PENDING");
        // 289.99 + 2.50 domestic shipping
        assertThat(payment.get("amount").decimalValue()).isEqualByComparingTo("292.49");

        send(asJson(asAdmin(patch("/api/admin/payments/" + payment.get("id").asLong() + "/status")), "{\"status\":\"PAID\"}"), 200);
        mockMvc.perform(asAdmin(get("/api/admin/orders/" + orderId + "/payments")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PAID"));
        mockMvc.perform(asAdmin(get("/api/admin/orders/" + orderId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        send(asJson(asAdmin(post("/api/admin/payments")), body.replace("pi_http_1", "pi_http_2")), 422);
    }

    @Test
    void supportTicketFlow() throws Exception {
        long id = json(send(asJson(post("/api/support-tickets"),
                "{\"reason\":\"Billing\",\"summary\":\"Charged twice\"}"), 201)).get("id").asLong();
        send(asJson(post("/api/support-tickets"), "{\"reason\":\"\",\"summary\":\"\"}"), 400);

        send(get("/api/admin/support-tickets"), 401);
        mockMvc.perform(asAdmin(get("/api/admin/support-tickets")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());
        send(asJson(asAdmin(patch("/api/admin/support-tickets/" + id + "/status")), "{\"status\":\"CLOSED\"}"), 200);
        send(asJson(asAdmin(patch("/api/admin/support-tickets/" + id + "/status")), "{\"status\":\"OPEN\"}"), 422);
    }

    @Test
    void dashboardShowsTheNewQueues() throws Exception {
        mockMvc.perform(asAdmin(get("/api/dashboard/kpis")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedBookings").exists())
                .andExpect(jsonPath("$.openWarrantyClaims").exists())
                .andExpect(jsonPath("$.openSupportTickets").exists());
    }
}
