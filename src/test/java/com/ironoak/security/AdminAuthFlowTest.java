package com.ironoak.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminUserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin sign-in end to end: sessions, refresh rotation and reuse detection, lockout, rate limits, password change. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminAuthFlowTest {

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
    private String bearer;

    @BeforeEach
    void admin() {
        adminUsers.deleteAll();
        adminUsers.save(new AdminUser("owner", "owner@ironoak.test",
                passwordEncoder.encode("correct-horse"), "Store Owner"));
        bearer = "Bearer " + jwtService.issueToken("owner");
    }

    private JsonNode send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request, String body) {
        return request.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer);
    }

    private static final String PASSWORD = "correct-horse";

    /** MockMvc request that claims to come from the given address. */
    private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder request) {
        request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
        return request;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode login(String ip, String username, String password, int expectedStatus) throws Exception {
        return send(json(from(ip, post("/api/admin/auth/login")),
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"), expectedStatus);
    }

    private JsonNode refresh(String ip, String refreshToken, int expectedStatus) throws Exception {
        return send(json(from(ip, post("/api/admin/auth/refresh")), "{\"refreshToken\":\"" + refreshToken + "\"}"),
                expectedStatus);
    }

    private void me(String accessToken, int expectedStatus) throws Exception {
        send(get("/api/admin/auth/me").header("Authorization", "Bearer " + accessToken), expectedStatus);
    }

    @Test
    void loginReturnsAShortLivedAccessTokenAndARefreshToken() throws Exception {
        JsonNode session = login("10.1.0.1", "owner", PASSWORD, 200);
        assertThat(session.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(session.get("expiresInMinutes").asLong()).isEqualTo(15);
        assertThat(session.get("refreshToken").asText()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(session.get("refreshExpiresAt").isNull()).isFalse();

        me(session.get("token").asText(), 200);
        JsonNode profile = send(get("/api/admin/auth/me").header("Authorization", "Bearer " + session.get("token").asText()), 200);
        assertThat(profile.get("username").asText()).isEqualTo("owner");
        send(get("/api/admin/auth/me"), 401);
        me(session.get("refreshToken").asText(), 401); // a refresh token is not an access token
    }

    @Test
    void wrongPasswordAndUnknownUserLookIdentical() throws Exception {
        String wrongPassword = mockMvc.perform(json(from("10.1.0.2", post("/api/admin/auth/login")),
                "{\"username\":\"owner\",\"password\":\"nope-nope-nope\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mockMvc.perform(json(from("10.1.0.2", post("/api/admin/auth/login")),
                "{\"username\":\"ghost\",\"password\":\"nope-nope-nope\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(wrongPassword).isEqualTo(unknownUser);
    }

    @Test
    void refreshRotatesTheTokenAndReusingAnOldOneEndsTheWholeSession() throws Exception {
        JsonNode first = login("10.1.0.3", "owner", PASSWORD, 200);
        JsonNode second = refresh("10.1.0.3", first.get("refreshToken").asText(), 200);
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(first.get("refreshToken").asText());
        me(second.get("token").asText(), 200);

        // the first token was already used: someone is replaying it, so the session dies
        refresh("10.1.0.3", first.get("refreshToken").asText(), 401);
        refresh("10.1.0.3", second.get("refreshToken").asText(), 401);
    }

    @Test
    void refreshRejectsGarbageAndAccessTokens() throws Exception {
        JsonNode session = login("10.1.0.4", "owner", PASSWORD, 200);
        refresh("10.1.0.4", "garbage", 401);
        // an access JWT is longer than any refresh token (200-char cap), so it is refused before lookup
        refresh("10.1.0.4", session.get("token").asText(), 400);
        send(json(from("10.1.0.4", post("/api/admin/auth/refresh")), "{}"), 400);
    }

    @Test
    void logoutRevokesTheSessionAndIsIdempotent() throws Exception {
        JsonNode session = login("10.1.0.5", "owner", PASSWORD, 200);
        String refreshToken = session.get("refreshToken").asText();
        send(json(from("10.1.0.5", post("/api/admin/auth/logout")), "{\"refreshToken\":\"" + refreshToken + "\"}"), 204);
        send(json(from("10.1.0.5", post("/api/admin/auth/logout")), "{\"refreshToken\":\"" + refreshToken + "\"}"), 204);
        send(json(from("10.1.0.5", post("/api/admin/auth/logout")), "{\"refreshToken\":\"unknown\"}"), 204);
        refresh("10.1.0.5", refreshToken, 401);
    }

    @Test
    void logoutAllEndsEverySessionOfTheAccount() throws Exception {
        JsonNode laptop = login("10.1.0.6", "owner", PASSWORD, 200);
        JsonNode phone = login("10.1.0.6", "owner", PASSWORD, 200);
        send(from("10.1.0.6", post("/api/admin/auth/logout-all")).header("Authorization", "Bearer " + laptop.get("token").asText()), 204);
        refresh("10.1.0.6", laptop.get("refreshToken").asText(), 401);
        refresh("10.1.0.6", phone.get("refreshToken").asText(), 401);
        send(post("/api/admin/auth/logout-all"), 401);
    }

    @Test
    void changingThePasswordKillsOldTokensAndSessionsAndEnforcesThePolicy() throws Exception {
        JsonNode old = login("10.1.0.7", "owner", PASSWORD, 200);
        String bearer = "Bearer " + old.get("token").asText();
        Thread.sleep(1100); // access-token issue times have one-second resolution

        send(json(from("10.1.0.7", post("/api/admin/auth/change-password")).header("Authorization", bearer),
                "{\"currentPassword\":\"wrong-wrong-wrong\",\"newPassword\":\"a-much-longer-passphrase\"}"), 422);
        send(json(from("10.1.0.7", post("/api/admin/auth/change-password")).header("Authorization", bearer),
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}"), 422);

        JsonNode renewed = send(json(from("10.1.0.7", post("/api/admin/auth/change-password")).header("Authorization", bearer),
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"a-much-longer-passphrase\"}"), 200);

        me(old.get("token").asText(), 401);                       // access token from before the change
        refresh("10.1.0.7", old.get("refreshToken").asText(), 401); // old session
        me(renewed.get("token").asText(), 200);                   // the caller gets a fresh session
        login("10.1.0.8", "owner", PASSWORD, 401);
        login("10.1.0.8", "owner", "a-much-longer-passphrase", 200);
    }

    @Test
    void fiveWrongPasswordsLockThatAddressOutEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("10.2.0.1", "owner", "wrong-" + i, 401);
        }
        mockMvc.perform(json(from("10.2.0.1", post("/api/admin/auth/login")),
                        "{\"username\":\"owner\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // the real owner, somewhere else, is not locked out by someone else's guesses
        login("10.2.0.2", "owner", PASSWORD, 200);
    }

    @Test
    void inventedUsernamesAreLockedLikeRealOnesSoAccountsCannotBeEnumerated() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("10.2.0.3", "ghost", "wrong-" + i, 401);
        }
        login("10.2.0.3", "ghost", "wrong-again", 429);
    }

    @Test
    void loginRequestsAreRateLimitedPerAddressAndForwardedForCannotBeSpoofed() throws Exception {
        for (int i = 0; i < 10; i++) {
            send(json(from("10.3.0.1", post("/api/admin/auth/login")).header("X-Forwarded-For", "9.9.9." + i), "{}"), 400);
        }
        send(json(from("10.3.0.1", post("/api/admin/auth/login")).header("X-Forwarded-For", "9.9.9.200"), "{}"), 429);
        send(json(from("10.3.0.2", post("/api/admin/auth/login")), "{}"), 400); // another address is unaffected
    }

    /** Sends requests until one is rejected with 429; the bucket refills slowly, so allow a little slack. */
    private void assertRejectedWithin(int extraAttempts, java.util.function.Supplier<MockHttpServletRequestBuilder> request,
                                      int normalStatus) throws Exception {
        for (int i = 0; i < extraAttempts; i++) {
            int status = mockMvc.perform(request.get()).andReturn().getResponse().getStatus();
            if (status == 429) {
                return;
            }
            assertThat(status).isEqualTo(normalStatus);
        }
        throw new AssertionError("no request was rate limited");
    }

    @Test
    void publicWriteEndpointsAreRateLimitedPerAddress() throws Exception {
        for (int i = 0; i < 30; i++) {
            send(json(from("10.4.0.1", post("/api/complaints")), "{}"), 400);
        }
        assertRejectedWithin(5, () -> json(from("10.4.0.1", post("/api/complaints")), "{}"), 400);
        send(json(from("10.4.0.2", post("/api/complaints")), "{}"), 400); // another address is unaffected
    }

    @Test
    void guessableOrderLookupsAreRateLimitedToo() throws Exception {
        for (int i = 0; i < 60; i++) {
            send(from("10.5.0.1", get("/api/orders/1")).param("email", "a" + i + "@example.com"), 404);
        }
        assertRejectedWithin(5, () -> from("10.5.0.1", get("/api/orders/1")).param("email", "z@example.com"), 404);
    }
}
