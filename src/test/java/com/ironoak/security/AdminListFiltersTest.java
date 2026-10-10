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

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff list filters: status, date range, category and free-text search across the admin queues. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminListFiltersTest {

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

    private boolean contains(JsonNode page, long id) {
        for (JsonNode row : page.get("content")) {
            if (row.get("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    private long placeOrder(String name, String email) throws Exception {
        String body = TestOrders.webJson(name, email, "PRODUCT", products.findBySku("IO-AICO-001").orElseThrow().getId(), 1);
        return send(admin(post("/api/orders"), body), 201).get("id").asLong();
    }

    @Test
    void listsAreStaffOnly() throws Exception {
        for (String path : new String[] { "orders", "complaints", "bookings", "support-tickets", "warranty-claims",
                "payments", "customers" }) {
            send(get("/api/admin/" + path), 401);
        }
    }

    @Test
    void ordersFilterByStatusDateChannelItemTypeAndSearch() throws Exception {
        long mine = placeOrder("Filter Fiona", "fiona@example.com");
        long other = placeOrder("Someone Else", "else@example.com");
        send(admin(patch("/api/admin/orders/" + other + "/status"), "{\"status\":\"CANCELLED\"}"), 200);

        assertThat(contains(send(admin(get("/api/admin/orders").param("status", "PENDING_PAYMENT")), 200), mine)).isTrue();
        JsonNode cancelled = send(admin(get("/api/admin/orders").param("status", "CANCELLED")), 200);
        assertThat(contains(cancelled, other)).isTrue();
        assertThat(contains(cancelled, mine)).isFalse();
        JsonNode both = send(admin(get("/api/admin/orders").param("status", "PENDING_PAYMENT", "CANCELLED")), 200);
        assertThat(contains(both, mine) && contains(both, other)).isTrue();

        assertThat(contains(send(admin(get("/api/admin/orders").param("q", "FIONA")), 200), other)).isFalse();
        assertThat(contains(send(admin(get("/api/admin/orders").param("q", "fiona@")), 200), mine)).isTrue();
        assertThat(contains(send(admin(get("/api/admin/orders").param("q", String.valueOf(mine))), 200), mine)).isTrue();

        assertThat(contains(send(admin(get("/api/admin/orders").param("itemType", "PRODUCT")), 200), mine)).isTrue();
        assertThat(contains(send(admin(get("/api/admin/orders").param("itemType", "SERVICE")), 200), mine)).isFalse();
        assertThat(contains(send(admin(get("/api/admin/orders").param("channel", "ADMIN_MANUAL")), 200), mine)).isFalse();
        assertThat(contains(send(admin(get("/api/admin/orders").param("channel", "WEB_CHECKOUT")), 200), mine)).isTrue();
        String number = send(admin(get("/api/admin/orders/" + mine)), 200).get("orderNumber").asText();
        assertThat(contains(send(admin(get("/api/admin/orders").param("q", number.toLowerCase())), 200), mine)).isTrue();

        String today = LocalDate.now(java.time.ZoneOffset.UTC).toString();
        assertThat(contains(send(admin(get("/api/admin/orders").param("from", today).param("to", today)), 200), mine)).isTrue();
        String tomorrow = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1).toString();
        assertThat(contains(send(admin(get("/api/admin/orders").param("from", tomorrow)), 200), mine)).isFalse();
        send(admin(get("/api/admin/orders").param("from", tomorrow).param("to", today)), 422);
        send(admin(get("/api/admin/orders").param("status", "NOT_A_STATUS")), 400);
    }

    @Test
    void complaintsTicketsAndCustomersFilterByStatusDateAndText() throws Exception {
        String when = OffsetDateTime.now().minusHours(1).toString();
        long complaint = send(admin(post("/api/complaints"), "{\"customerName\":\"Cora Complaint\",\"complaintDatetime\":\""
                + when + "\",\"product\":\"Cordless Saw\",\"description\":\"Blade wobbles\"}"), 201).get("id").asLong();
        assertThat(contains(send(admin(get("/api/admin/complaints").param("status", "PENDING").param("q", "wobble")), 200), complaint)).isTrue();
        assertThat(contains(send(admin(get("/api/admin/complaints").param("status", "RESOLVED")), 200), complaint)).isFalse();
        assertThat(contains(send(admin(get("/api/admin/complaints").param("q", "no such text")), 200), complaint)).isFalse();

        long ticket = send(admin(post("/api/support-tickets"),
                "{\"customerEmail\":\"tess@example.com\",\"reason\":\"Billing\",\"summary\":\"Charged twice\"}"), 201).get("id").asLong();
        assertThat(contains(send(admin(get("/api/admin/support-tickets").param("status", "OPEN").param("q", "charged")), 200), ticket)).isTrue();
        assertThat(contains(send(admin(get("/api/admin/support-tickets").param("status", "CLOSED")), 200), ticket)).isFalse();

        long order = placeOrder("Carl Customer", "carl@example.com");
        JsonNode customers = send(admin(get("/api/admin/customers").param("q", "CARL")), 200);
        assertThat(customers.get("content")).hasSize(1);
        assertThat(customers.get("content").get(0).get("email").asText()).isEqualTo("carl@example.com");
        send(admin(get("/api/admin/customers/" + customers.get("content").get(0).get("id").asLong())), 200);
        assertThat(order).isPositive();
    }

    @Test
    void bookingsDefaultToOpenAndFilterByCategoryServiceDateAndText() throws Exception {
        var service = serviceOfferings.findByCode("PREVENTIVE_MAINTENANCE").orElseThrow();
        OffsetDateTime visit = OffsetDateTime.now(java.time.ZoneOffset.UTC).plusDays(4);
        String when = visit.toString();
        long id = send(admin(post("/api/bookings"), "{\"customerName\":\"Bo Ker\",\"customerEmail\":\"bo@example.com\","
                + "\"serviceOfferingId\":" + service.getId() + ",\"locationAddress\":\"Plant 7\",\"scheduledAt\":\"" + when + "\"}"), 201)
                .get("id").asLong();

        assertThat(contains(send(admin(get("/api/admin/bookings")), 200), id)).isTrue();
        long categoryId = service.getCategory().getId();
        assertThat(contains(send(admin(get("/api/admin/bookings").param("categoryId", String.valueOf(categoryId))), 200), id)).isTrue();
        assertThat(contains(send(admin(get("/api/admin/bookings").param("categoryId", "999999")), 200), id)).isFalse();
        assertThat(contains(send(admin(get("/api/admin/bookings").param("serviceId", String.valueOf(service.getId()))), 200), id)).isTrue();
        assertThat(contains(send(admin(get("/api/admin/bookings").param("q", "plant 7")), 200), id)).isTrue();
        String day = visit.toLocalDate().toString(); // the filter takes UTC dates
        assertThat(contains(send(admin(get("/api/admin/bookings").param("from", day).param("to", day)), 200), id)).isTrue();

        send(admin(post("/api/bookings/" + id + "/cancel").param("email", "bo@example.com")), 200);
        assertThat(contains(send(admin(get("/api/admin/bookings")), 200), id)).isFalse(); // closed ones leave the default queue
        assertThat(contains(send(admin(get("/api/admin/bookings").param("status", "CANCELLED")), 200), id)).isTrue();
    }

    @Test
    void paymentsListFiltersByStatusProviderAndOrder() throws Exception {
        long order = placeOrder("Pay Pal", "pay@example.com");
        long payment = send(admin(post("/api/admin/payments"),
                "{\"orderId\":" + order + ",\"provider\":\"manual\",\"providerReference\":\"REF-42\"}"), 201).get("id").asLong();
        assertThat(contains(send(admin(get("/api/admin/payments").param("status", "PENDING").param("provider", "manual")), 200), payment)).isTrue();
        assertThat(contains(send(admin(get("/api/admin/payments").param("q", String.valueOf(order))), 200), payment)).isTrue();
        assertThat(contains(send(admin(get("/api/admin/payments").param("q", "ref-42")), 200), payment)).isTrue();
        assertThat(contains(send(admin(get("/api/admin/payments").param("status", "PAID")), 200), payment)).isFalse();
    }

    @Test
    void productAndServiceListsFilterByCategoryStockAndPricing() throws Exception {
        JsonNode lowStock = send(admin(get("/api/admin/products").param("maxStock", "0")), 200);
        for (JsonNode row : lowStock.get("content")) {
            assertThat(row.get("stockQuantity").asInt()).isZero();
        }
        JsonNode hand = send(admin(get("/api/admin/products").param("category", "Hand Tools").param("size", "50")), 200);
        assertThat(hand.get("page").get("totalElements").asInt()).isEqualTo(27);

        JsonNode quotes = send(admin(get("/api/admin/services").param("pricingType", "QUOTE")), 200);
        for (JsonNode row : quotes) {
            assertThat(row.get("pricingType").asText()).isEqualTo("QUOTE");
        }
        JsonNode missing = send(admin(get("/api/admin/machines").param("q", "no-such-machine")), 200);
        assertThat(missing).isEmpty();
    }
}
