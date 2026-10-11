package com.ironoak.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.config.AdminBootstrap;
import com.ironoak.domain.AdminUser;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.services.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** First-admin bootstrap, staff accounts, forced password change and disabling. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminStaffTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final String TEMP = "temporary-pass-123";

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
    private AuditService audit;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        adminUsers.deleteAll();
    }

    private JsonNode send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode login(String username, String password, int expectedStatus) throws Exception {
        return send(json(post("/api/admin/auth/login"),
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"), expectedStatus);
    }

    private String owner() {
        adminUsers.save(new AdminUser("owner", "owner@ironoak.test", passwordEncoder.encode("owner-password-1"), "Owner"));
        return "Bearer " + jwtService.issueToken("owner");
    }

    private AdminBootstrap bootstrap(String username, String password) {
        MockEnvironment env = new MockEnvironment()
                .withProperty("BOOTSTRAP_ADMIN_USERNAME", username)
                .withProperty("BOOTSTRAP_ADMIN_EMAIL", username + "@ironoak.test")
                .withProperty("BOOTSTRAP_ADMIN_PASSWORD", password);
        return new AdminBootstrap(adminUsers, passwordEncoder, audit, env);
    }

    @Test
    void theBootstrapCreatesOneAdminOnceAndRefusesAWeakPassword() throws Exception {
        assertThatThrownBy(() -> bootstrap("bryan", "short").run(null)).isInstanceOf(BusinessRuleException.class);
        assertThat(adminUsers.count()).isZero();

        bootstrap("bryan", "first-owner-pass").run(null);
        bootstrap("intruder", "another-strong-pass").run(null); // an admin exists: ignored

        assertThat(adminUsers.findAll()).extracting(AdminUser::getUsername).containsExactly("bryan");
        assertThat(adminUsers.findByUsername("bryan").orElseThrow().isMustChangePassword()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from admin_audit_log where action = 'ADMIN_BOOTSTRAP'",
                Integer.class)).isEqualTo(1);
        assertThat(login("bryan", "first-owner-pass", 200).get("passwordChangeRequired").asBoolean()).isTrue();
    }

    @Test
    void newStaffMustReplaceTheTemporaryPasswordBeforeAnythingElse() throws Exception {
        String ownerToken = owner();
        JsonNode created = send(json(post("/api/admin/staff").header("Authorization", ownerToken),
                "{\"username\":\"ana\",\"email\":\"Ana@IronOak.test\",\"fullName\":\"Ana\",\"temporaryPassword\":\""
                        + TEMP + "\"}"), 201);
        assertThat(created.get("email").asText()).isEqualTo("ana@ironoak.test");
        assertThat(created.get("passwordChangeRequired").asBoolean()).isTrue();

        JsonNode session = login("ana", TEMP, 200);
        assertThat(session.get("passwordChangeRequired").asBoolean()).isTrue();
        String pending = "Bearer " + session.get("token").asText();
        send(get("/api/admin/products").header("Authorization", pending), 403);
        send(get("/api/admin/auth/me").header("Authorization", pending), 200);

        JsonNode changed = send(json(post("/api/admin/auth/change-password").header("Authorization", pending),
                "{\"currentPassword\":\"" + TEMP + "\",\"newPassword\":\"my-own-passphrase-77\"}"), 200);
        assertThat(changed.get("passwordChangeRequired").asBoolean()).isFalse();
        send(get("/api/admin/products").header("Authorization", "Bearer " + changed.get("token").asText()), 200);
    }

    @Test
    void aDisabledAdminIsLockedOutAtOnce() throws Exception {
        String ownerToken = owner();
        long ownerId = adminUsers.findByUsername("owner").orElseThrow().getId();
        long anaId = send(json(post("/api/admin/staff").header("Authorization", ownerToken),
                "{\"username\":\"ana\",\"email\":\"ana@ironoak.test\",\"temporaryPassword\":\"" + TEMP + "\"}"), 201)
                .get("id").asLong();
        JsonNode session = login("ana", TEMP, 200);
        String anaToken = "Bearer " + session.get("token").asText();

        send(json(patch("/api/admin/staff/" + ownerId + "/active").header("Authorization", ownerToken),
                "{\"isActive\":false}"), 422); // not yourself
        send(json(patch("/api/admin/staff/" + anaId + "/active").header("Authorization", ownerToken),
                "{\"isActive\":false}"), 200);

        send(get("/api/admin/auth/me").header("Authorization", anaToken), 401);
        send(json(post("/api/admin/auth/refresh"),
                "{\"refreshToken\":\"" + session.get("refreshToken").asText() + "\"}"), 401);
        login("ana", TEMP, 401);
        assertThat(jdbc.queryForObject("select count(*) from admin_audit_log where action = 'STAFF_DISABLE'",
                Integer.class)).isEqualTo(1);
    }
}
