package com.ironoak.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.repository.ProductRepository;
import com.ironoak.repository.ToolCategoryRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff catalog CRUD: auth, validation, duplicate keys, stock safety, and the public storefront view. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminCatalogControllerTest {

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
    private ToolCategoryRepository toolCategories;

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

    private String productBody(long toolCategoryId, String sku, String price) {
        return "{\"toolCategoryId\":" + toolCategoryId + ",\"sku\":\"" + sku + "\",\"name\":\"Test Hammer\","
                + "\"category\":\"Hand Tools\",\"price\":" + price + ",\"stockQuantity\":10,\"warrantyMonths\":6}";
    }

    @Test
    void catalogEndpointsAreStaffOnly() throws Exception {
        send(get("/api/admin/products"), 401);
        send(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON).content("{}"), 401);
        send(get("/api/admin/machines"), 401);
        send(get("/api/admin/services"), 401);
    }

    @Test
    void productLifecycleKeepsVisionNameInStepWithTheToolCategory() throws Exception {
        long wrench = toolCategories.findByModelLabel("wrench_ai").orElseThrow().getId();
        long drill = toolCategories.findByModelLabel("electric_drill_ai").orElseThrow().getId();

        JsonNode created = send(admin(post("/api/admin/products"), productBody(wrench, "T-HAMMER-1", "19.99")), 201);
        long id = created.get("id").asLong();
        assertThat(created.get("visionName").asText()).isEqualTo("wrench_ai");
        assertThat(created.get("isActive").asBoolean()).isTrue();
        assertThat(created.get("createdAt").isNull()).isFalse();

        // moving to another tool category re-derives visionName; stock is untouched by PUT
        JsonNode updated = send(admin(put("/api/admin/products/" + id),
                "{\"toolCategoryId\":" + drill + ",\"sku\":\"T-HAMMER-1\",\"name\":\"Renamed\",\"category\":\"Power Tools\","
                        + "\"price\":24.50,\"warrantyMonths\":12}"), 200);
        assertThat(updated.get("visionName").asText()).isEqualTo("electric_drill_ai");
        assertThat(updated.get("price").decimalValue()).isEqualByComparingTo("24.50");
        assertThat(updated.get("stockQuantity").asInt()).isEqualTo(10);

        // deactivated products leave the storefront but stay visible to staff
        send(get("/api/products/" + id), 200);
        send(admin(patch("/api/admin/products/" + id + "/active"), "{\"isActive\":false}"), 200);
        send(get("/api/products/" + id), 404);
        send(admin(get("/api/admin/products/" + id)), 200);
        JsonNode inactive = send(admin(get("/api/admin/products").param("active", "false")), 200);
        assertThat(inactive.get("content")).anyMatch(p -> p.get("id").asLong() == id);
    }

    @Test
    void stockAdjustmentsAreAtomicAndNeverGoNegative() throws Exception {
        long wrench = toolCategories.findByModelLabel("wrench_ai").orElseThrow().getId();
        long id = send(admin(post("/api/admin/products"), productBody(wrench, "T-STOCK-1", "5.00")), 201).get("id").asLong();

        assertThat(send(admin(patch("/api/admin/products/" + id + "/stock"), "{\"delta\":5}"), 200)
                .get("stockQuantity").asInt()).isEqualTo(15);
        assertThat(send(admin(patch("/api/admin/products/" + id + "/stock"), "{\"delta\":-15}"), 200)
                .get("stockQuantity").asInt()).isZero();
        send(admin(patch("/api/admin/products/" + id + "/stock"), "{\"delta\":-1}"), 422);
        send(admin(patch("/api/admin/products/999999/stock"), "{\"delta\":1}"), 404);
    }

    @Test
    void productValidationAndDuplicateSkus() throws Exception {
        long wrench = toolCategories.findByModelLabel("wrench_ai").orElseThrow().getId();
        send(admin(post("/api/admin/products"), productBody(wrench, "T-DUP-1", "5.00")), 201);
        send(admin(post("/api/admin/products"), productBody(wrench, "T-DUP-1", "6.00")), 409);
        send(admin(post("/api/admin/products"), productBody(wrench, "T-NEG-1", "-1")), 400);
        send(admin(post("/api/admin/products"), productBody(999999, "T-NOCAT-1", "1.00")), 404);
        send(admin(post("/api/admin/products"), "{}"), 400);
        assertThat(products.findBySku("T-NEG-1")).isEmpty();
    }

    @Test
    void machineCrudEnforcesTheSpindleRangeAndUniqueCode() throws Exception {
        String body = "{\"modelCode\":\"TEST-1\",\"name\":\"Test Mill\",\"powerKw\":2.2,\"spindleMinRpm\":100,"
                + "\"spindleMaxRpm\":5000,\"tableLengthMm\":500,\"tableWidthMm\":200,\"price\":9999.00,\"warrantyMonths\":12}";
        long id = send(admin(post("/api/admin/machines"), body), 201).get("id").asLong();
        send(admin(post("/api/admin/machines"), body), 409);
        send(admin(put("/api/admin/machines/" + id), body.replace("\"spindleMaxRpm\":5000", "\"spindleMaxRpm\":50")), 422);

        JsonNode updated = send(admin(put("/api/admin/machines/" + id), body.replace("9999.00", "8888.00")), 200);
        assertThat(updated.get("price").decimalValue()).isEqualByComparingTo("8888.00");
        send(admin(patch("/api/admin/machines/" + id + "/active"), "{\"isActive\":false}"), 200);
        send(get("/api/services/machines/TEST-1"), 404);
    }

    @Test
    void servicePricingRulesAreCheckedAndStaleFieldsCleared() throws Exception {
        long category = send(admin(get("/api/admin/service-categories")), 200).get(0).get("id").asLong();
        String hourly = "{\"serviceOfferingCategoryId\":" + category + ",\"code\":\"TEST_SVC\",\"name\":\"Test Service\","
                + "\"pricingType\":\"HOURLY\",\"hourlyRate\":80,\"estimatedMinHours\":1,\"estimatedMaxHours\":3,\"priceUnit\":\"hour\"}";
        send(admin(post("/api/admin/services"), hourly.replace("\"hourlyRate\":80,", "")), 422);
        long id = send(admin(post("/api/admin/services"), hourly), 201).get("id").asLong();
        send(admin(post("/api/admin/services"), hourly), 409);

        // switching to QUOTE drops every stored price
        JsonNode quote = send(admin(put("/api/admin/services/" + id),
                "{\"serviceOfferingCategoryId\":" + category + ",\"code\":\"TEST_SVC\",\"name\":\"Test Service\","
                        + "\"pricingType\":\"QUOTE\",\"hourlyRate\":80}"), 200);
        assertThat(quote.get("hourlyRate").isNull()).isTrue();
        assertThat(quote.get("priceUnit").isNull()).isTrue();
    }

    @Test
    void toolCategoryTextIsEditableButTheLabelIsNot() throws Exception {
        var category = toolCategories.findByModelLabel("wrench_ai").orElseThrow();
        JsonNode updated = send(admin(put("/api/admin/tool-categories/" + category.getId()),
                "{\"displayName\":\"Wrench (edited)\",\"synonyms\":[\"spanner\",\"wrench\"],\"description\":\"d\"}"), 200);
        assertThat(updated.get("displayName").asText()).isEqualTo("Wrench (edited)");
        assertThat(updated.get("modelLabel").asText()).isEqualTo("wrench_ai");
        send(admin(put("/api/admin/tool-categories/" + category.getId()), "{\"displayName\":\"\"}"), 400);
    }
}
