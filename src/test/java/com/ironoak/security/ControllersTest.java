package com.ironoak.security;

import com.ironoak.TestOrders;
import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.repository.ProductRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level behaviour of the controllers, including which routes need a token. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ControllersTest {

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
    private ProductRepository products;

    private String bearer;

    @BeforeEach
    void admin() {
        adminUsers.deleteAll();
        adminUsers.save(new AdminUser("owner", "owner@ironoak.test",
                passwordEncoder.encode("correct-horse"), "Store Owner"));
        bearer = "Bearer " + jwtService.issueToken("owner");
    }

    @Test
    void publicCatalog() throws Exception {
        mockMvc.perform(get("/api/products?size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(87));
        mockMvc.perform(get("/api/products?category=Hand Tools&size=100"))
                .andExpect(jsonPath("$.page.totalElements").value(27));
        mockMvc.perform(get("/api/products/sku/IO-AICO-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visionName").value("air_compressors"));
        mockMvc.perform(get("/api/products/by-vision/wrench_ai"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/products/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/tool-categories"))
                .andExpect(jsonPath("$.length()").value(87));
        mockMvc.perform(get("/api/products/sku/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found: NOPE"));
        mockMvc.perform(get("/api/products?size=5000"))
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void publicServicesAndMachines() throws Exception {
        mockMvc.perform(get("/api/services")).andExpect(jsonPath("$.length()").value(6));
        mockMvc.perform(get("/api/services/EMERGENCY_REPAIR"))
                .andExpect(jsonPath("$.pricingType").value("HOURLY"));
        mockMvc.perform(get("/api/services/machines")).andExpect(jsonPath("$.length()").value(4));
        mockMvc.perform(get("/api/services/machines/BM-200"))
                .andExpect(jsonPath("$.name").value("Benchtop Mill"));
        mockMvc.perform(get("/api/services/plumbing")).andExpect(status().isNotFound());
    }

    @Test
    void guestCheckoutIsPublicAndValidated() throws Exception {
        Long id = products.findBySku("IO-AICO-001").orElseThrow().getId();
        mockMvc.perform(post("/api/orders").contentType("application/json")
                        .content(TestOrders.webJson("Jane Doe", "jane@example.com", "PRODUCT", id, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.channel").value("WEB_CHECKOUT"))
                .andExpect(jsonPath("$.orderNumber").isNotEmpty())
                .andExpect(jsonPath("$.shippingCost").value(2.5))
                .andExpect(jsonPath("$.grandTotal").value(292.49));

        // a web order needs a contact, and an address for goods
        mockMvc.perform(post("/api/orders").contentType("application/json")
                        .content("{\"items\":[{\"itemType\":\"PRODUCT\",\"referenceId\":" + id + ",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders").contentType("application/json")
                        .content(TestOrders.webJson("Jane Doe", "jane@example.com", "PRODUCT", id, 1)
                                .replace("\"country\":\"CO\"", "\"country\":\"FR\"")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/orders").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/orders").contentType("application/json")
                        .content("{\"items\":[{\"itemType\":\"PRODUCT\",\"referenceId\":999999,\"quantity\":1}]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders").contentType("application/json")
                        .content(TestOrders.webJson("Jane Doe", "jane@example.com", "PRODUCT", id, 100000)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminEndpointsNeedAToken() throws Exception {
        mockMvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/complaints")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/kpis")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/orders").header("Authorization", bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/kpis").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingComplaints").exists());
    }

    @Test
    void complaintLifecycle() throws Exception {
        String body = """
                {"customerName":"Jane Doe","complaintDatetime":"2026-10-01T10:00:00Z",
                 "product":"Jigsaw","description":"Blade snapped on first use"}
                """;
        String created = mockMvc.perform(post("/api/complaints").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/complaints").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/complaints/" + id + "/status").contentType("application/json")
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/complaints/" + id + "/status").header("Authorization", bearer)
                        .contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
