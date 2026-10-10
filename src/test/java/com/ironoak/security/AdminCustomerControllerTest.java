package com.ironoak.security;

import com.ironoak.TestOrders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.repository.ProductRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff customer CRUD: auth, validation, duplicate emails, partial updates, and guarded deletes. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminCustomerControllerTest {

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

    private long create(String body) throws Exception {
        return send(admin(post("/api/admin/customers"), body), 201).get("id").asLong();
    }

    @Test
    void customerEndpointsAreStaffOnly() throws Exception {
        String json = "{\"name\":\"X\"}";
        send(post("/api/admin/customers").contentType(MediaType.APPLICATION_JSON).content(json), 401);
        send(put("/api/admin/customers/1").contentType(MediaType.APPLICATION_JSON).content(json), 401);
        send(patch("/api/admin/customers/1").contentType(MediaType.APPLICATION_JSON).content(json), 401);
        send(delete("/api/admin/customers/1"), 401);
        send(get("/api/admin/customers/1"), 401);
    }

    @Test
    void createNormalisesTheEmailAndRejectsDuplicatesAndBadInput() throws Exception {
        JsonNode created = send(admin(post("/api/admin/customers"),
                "{\"name\":\"  Ada Lovelace \",\"email\":\"ADA@Example.COM\",\"phone\":\"555-0100\",\"address\":\"1 Main St\"}"), 201);
        assertThat(created.get("name").asText()).isEqualTo("Ada Lovelace");
        assertThat(created.get("email").asText()).isEqualTo("ada@example.com");
        assertThat(created.get("createdAt").isNull()).isFalse();
        send(admin(get("/api/admin/customers/" + created.get("id").asLong())), 200);

        send(admin(post("/api/admin/customers"), "{\"name\":\"Other\",\"email\":\"Ada@example.com\"}"), 409);
        send(admin(post("/api/admin/customers"), "{\"name\":\"\"}"), 400);
        send(admin(post("/api/admin/customers"), "{\"name\":\"Bad\",\"email\":\"not-an-email\"}"), 400);

        // several customers without an email are fine
        create("{\"name\":\"No Email One\"}");
        create("{\"name\":\"No Email Two\"}");
        send(admin(get("/api/admin/customers/999999")), 404);
    }

    @Test
    void putReplacesEverythingAndPatchChangesOnlyWhatIsSent() throws Exception {
        long id = create("{\"name\":\"Grace\",\"email\":\"grace@example.com\",\"phone\":\"1\",\"address\":\"A\"}");
        long other = create("{\"name\":\"Other\",\"email\":\"other@example.com\"}");

        JsonNode patched = send(admin(patch("/api/admin/customers/" + id), "{\"phone\":\"2\"}"), 200);
        assertThat(patched.get("phone").asText()).isEqualTo("2");
        assertThat(patched.get("email").asText()).isEqualTo("grace@example.com");
        assertThat(patched.get("address").asText()).isEqualTo("A");

        JsonNode cleared = send(admin(patch("/api/admin/customers/" + id), "{\"address\":\"\"}"), 200);
        assertThat(cleared.get("address").isNull()).isTrue();
        send(admin(patch("/api/admin/customers/" + id), "{\"name\":\"  \"}"), 422);
        send(admin(patch("/api/admin/customers/" + id), "{\"email\":\"OTHER@example.com\"}"), 409);
        // keeping your own email is not a duplicate
        send(admin(patch("/api/admin/customers/" + id), "{\"email\":\"GRACE@example.com\"}"), 200);

        JsonNode replaced = send(admin(put("/api/admin/customers/" + id), "{\"name\":\"Grace Hopper\"}"), 200);
        assertThat(replaced.get("name").asText()).isEqualTo("Grace Hopper");
        assertThat(replaced.get("email").isNull()).isTrue();
        assertThat(replaced.get("phone").isNull()).isTrue();
        send(admin(put("/api/admin/customers/" + id), "{\"name\":\"Grace\",\"email\":\"other@example.com\"}"), 409);
        send(admin(put("/api/admin/customers/999999"), "{\"name\":\"Nobody\"}"), 404);
        assertThat(other).isPositive();
    }

    @Test
    void deleteWorksOnlyForACustomerNothingReferences() throws Exception {
        long free = create("{\"name\":\"Free To Go\",\"email\":\"free@example.com\"}");
        send(admin(delete("/api/admin/customers/" + free)), 204);
        send(admin(get("/api/admin/customers/" + free)), 404);
        send(admin(delete("/api/admin/customers/" + free)), 404);

        // checkout creates a customer that the order then refers to
        String order = TestOrders.webJson("Buyer", "buyer@example.com", "PRODUCT",
                products.findBySku("IO-AICO-001").orElseThrow().getId(), 1);
        send(admin(post("/api/orders"), order), 201);
        long buyer = send(admin(get("/api/admin/customers").param("q", "buyer@")), 200)
                .get("content").get(0).get("id").asLong();
        send(admin(delete("/api/admin/customers/" + buyer)), 409);
        send(admin(get("/api/admin/customers/" + buyer)), 200);
    }
}
