package com.ironoak.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.domain.AdminUser;
import com.ironoak.dto.request.AdminLoginRequest;
import com.ironoak.repository.AdminUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityConfigTest {

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
    private AdminUserRepository adminUsers;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedAdmin() {
        adminUsers.deleteAll();
        adminUsers.save(new AdminUser("owner", "owner@ironoak.test",
                passwordEncoder.encode("correct-horse"), "Store Owner"));
    }

    // The controllers are still stubs, so a permitted route reaches the dispatcher and
    // 404s. 404 means "security let it through" - which is exactly what is under test.
    private static final int PASSED_SECURITY = 404;

    @Test
    void storefrontBrowsingIsPublic() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().is(PASSED_SECURITY));
        mockMvc.perform(get("/api/services/plumbing"))
                .andExpect(status().is(PASSED_SECURITY));
        mockMvc.perform(get("/api/materials"))
                .andExpect(status().is(PASSED_SECURITY));
    }

    @Test
    void piperAndGuestCheckoutArePublic() throws Exception {
        mockMvc.perform(post("/api/chat/messages").contentType("application/json").content("{}"))
                .andExpect(status().is(PASSED_SECURITY));
        mockMvc.perform(post("/api/orders").contentType("application/json").content("{}"))
                .andExpect(status().is(PASSED_SECURITY));
    }

    @Test
    void dashboardIsRejectedWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/dashboard/kpis"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardIsReachableWithAValidToken() throws Exception {
        String token = jwtService.issueToken("owner");

        mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", "Bearer " + token))
                .andExpect(status().is(PASSED_SECURITY));
    }

    @Test
    void rejectsTamperedAndUnknownTokens() throws Exception {
        String valid = jwtService.issueToken("owner");
        String tampered = valid.substring(0, valid.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        // Correctly signed, but the account no longer exists.
        String ghost = jwtService.issueToken("deleted-user");
        mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", "Bearer " + ghost))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIssuesAWorkingToken() throws Exception {
        String body = objectMapper.writeValueAsString(
                new AdminLoginRequest("owner", "correct-horse"));

        String response = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.fullName").value("Store Owner"))
                .andExpect(jsonPath("$.expiresInMinutes").value(120))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        assertThat(jwtService.extractUsername(token)).contains("owner");

        mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", "Bearer " + token))
                .andExpect(status().is(PASSED_SECURITY));

        assertThat(adminUsers.findByUsername("owner").orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        String body = objectMapper.writeValueAsString(
                new AdminLoginRequest("owner", "wrong"));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }
}
