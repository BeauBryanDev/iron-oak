package com.ironoak.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminUserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin audit trail: logins and catalog changes are recorded and readable by staff only. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminAuditTest {

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
    private JdbcTemplate jdbc;
    private String bearer;

    @BeforeEach
    void admin() {
        jdbc.update("delete from admin_audit_log");
        if (adminUsers.findByUsername("auditor").isEmpty()) {
            adminUsers.save(new AdminUser("auditor", "auditor@ironoak.test",
                    passwordEncoder.encode("correct-horse-battery"), "Auditor"));
        }
        bearer = "Bearer " + jwtService.issueToken("auditor");
    }

    private JsonNode send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private JsonNode audit(String query) throws Exception {
        return send(get("/api/admin/audit" + query).header("Authorization", bearer), 200).get("content");
    }

    @Test
    void failedAndSuccessfulLoginsAreRecorded() throws Exception {
        String login = "{\"username\":\"auditor\",\"password\":\"%s\"}";
        send(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(login.formatted("wrong-password")), 401);
        send(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(login.formatted("correct-horse-battery")).header("User-Agent", "audit-test"), 200);

        JsonNode entries = audit("?username=auditor");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).get("action").asText()).isEqualTo("LOGIN_SUCCESS"); // newest first
        assertThat(entries.get(0).get("adminUserId").isNull()).isFalse();
        assertThat(entries.get(0).get("userAgent").asText()).isEqualTo("audit-test");
        assertThat(entries.get(1).get("action").asText()).isEqualTo("LOGIN_FAILED"); // kept despite the rollback
        assertThat(entries.get(0).get("ipAddress").asText()).isNotBlank();
    }

    @Test
    void aPriceChangeKeepsOnlyTheChangedFieldsAndTheTrailIsStaffOnly() throws Exception {
        long id = jdbc.queryForObject("select id from product where sku = 'IO-AICO-001'", Long.class);
        ObjectNode product = (ObjectNode) send(get("/api/admin/products/" + id).header("Authorization", bearer), 200);
        String oldPrice = product.get("price").asText();
        product.put("price", 299.99);
        send(put("/api/admin/products/" + id).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(product.toString()), 200);

        JsonNode entries = audit("?action=PRODUCT_UPDATE&resourceType=product&resourceId=" + id);
        assertThat(entries).hasSize(1);
        JsonNode entry = entries.get(0);
        assertThat(entry.get("username").asText()).isEqualTo("auditor");
        assertThat(entry.get("oldValue").toString()).isEqualTo("{\"price\":" + oldPrice + "}");
        assertThat(entry.get("newValue").toString()).isEqualTo("{\"price\":299.99}");

        send(get("/api/admin/audit"), 401);
    }
}
