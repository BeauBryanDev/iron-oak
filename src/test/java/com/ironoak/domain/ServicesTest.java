package com.ironoak.domain;

import com.ironoak.domain.enums.ComplaintStatus;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.dto.request.ComplaintRequest;
import com.ironoak.dto.request.CreateOrderRequest;
import com.ironoak.dto.request.CreateOrderRequest.Item;
import com.ironoak.dto.response.OrderResponse;
import com.ironoak.exceptions.InvalidOrderException;
import com.ironoak.exceptions.OutOfStockException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.ProductRepository;
import com.ironoak.services.ComplaintService;
import com.ironoak.services.DashboardService;
import com.ironoak.services.OrderService;
import com.ironoak.services.ProductService;
import com.ironoak.services.ServiceCatalogService;
import com.ironoak.services.ToolCategoryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class ServicesTest {

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
    private OrderService orders;
    @Autowired
    private ProductService productService;
    @Autowired
    private ToolCategoryService toolCategories;
    @Autowired
    private ServiceCatalogService catalog;
    @Autowired
    private ComplaintService complaints;
    @Autowired
    private DashboardService dashboard;
    @Autowired
    private ProductRepository products;
    @Autowired
    private EntityManager em;

    private Long productId(String sku) {
        return products.findBySku(sku).orElseThrow().getId();
    }

    private Long serviceId(String code) {
        return catalog.getService(code).id();
    }

    private Long machineId(String code) {
        return catalog.getMachine(code).id();
    }

    private int stock(String sku) {
        return products.findBySku(sku).orElseThrow().getStockQuantity();
    }

    @Test
    void catalogServices() {
        assertThat(productService.list(null, null, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(87);
        assertThat(productService.list("Hand Tools", null, PageRequest.of(0, 50)).getTotalElements()).isEqualTo(27);
        assertThat(productService.getBySku("IO-AICO-001").visionName()).isEqualTo("air_compressors");
        assertThat(productService.findByVisionLabel("wrench_ai")).hasSize(1);
        assertThat(productService.categories()).contains("Hand Tools");
        assertThat(toolCategories.list()).hasSize(87);
        assertThat(toolCategories.getByModelLabel("wrench_ai").synonyms()).isNotEmpty();
        assertThat(catalog.listServices()).hasSize(6);
        assertThat(catalog.listMachines()).hasSize(4);
        assertThatThrownBy(() -> productService.getBySku("NOPE")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> toolCategories.getByModelLabel("nope")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createsMixedOrderPricedFromCatalogAndTakesStock() {
        int before = stock("IO-AICO-001");
        var request = new CreateOrderRequest("Jane Doe", "jane@example.com", null, List.of(
                new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), 2, null),
                new Item(OrderItemType.SERVICE, serviceId("PREVENTIVE_MAINTENANCE"), 1, null),
                new Item(OrderItemType.SERVICE, serviceId("EMERGENCY_REPAIR"), 1, new BigDecimal("3.0")),
                new Item(OrderItemType.MACHINE, machineId("BM-200"), 1, null)));

        OrderResponse order = orders.create(request, OrderChannel.AGENT_CHAT);

        // 2 x 289.99 + 240.00 + (180.00 x 3.0 = 540.00) + 4250.00
        assertThat(order.totalAmount()).isEqualByComparingTo("5609.98");
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.customerName()).isEqualTo("Jane Doe");
        assertThat(order.items()).hasSize(4);
        assertThat(stock("IO-AICO-001")).isEqualTo(before - 2);
    }

    @Test
    void outOfStockRejectsTheWholeOrder() {
        int before = stock("IO-AICO-001");
        var request = new CreateOrderRequest(null, null, null, List.of(
                new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), 1, null),
                new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), before + 5, null)));

        assertThatThrownBy(() -> orders.create(request, OrderChannel.AGENT_CHAT))
                .isInstanceOf(OutOfStockException.class);
    }

    @Test
    void rejectsQuoteOnlyAndBadHourlyServices() {
        var quote = new CreateOrderRequest(null, null, null,
                List.of(new Item(OrderItemType.SERVICE, serviceId("SPINDLE_TOOLING_SERVICE"), 1, null)));
        assertThatThrownBy(() -> orders.create(quote, OrderChannel.AGENT_CHAT))
                .isInstanceOf(InvalidOrderException.class).hasMessageContaining("quote");

        var noHours = new CreateOrderRequest(null, null, null,
                List.of(new Item(OrderItemType.SERVICE, serviceId("EMERGENCY_REPAIR"), 1, null)));
        assertThatThrownBy(() -> orders.create(noHours, OrderChannel.AGENT_CHAT))
                .isInstanceOf(InvalidOrderException.class);

        var tooMany = new CreateOrderRequest(null, null, null,
                List.of(new Item(OrderItemType.SERVICE, serviceId("EMERGENCY_REPAIR"), 1, new BigDecimal("9"))));
        assertThatThrownBy(() -> orders.create(tooMany, OrderChannel.AGENT_CHAT))
                .isInstanceOf(InvalidOrderException.class);

        var missing = new CreateOrderRequest(null, null, null,
                List.of(new Item(OrderItemType.PRODUCT, 999999L, 1, null)));
        assertThatThrownBy(() -> orders.create(missing, OrderChannel.AGENT_CHAT))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void statusTransitionsAndCancelRestoresStock() {
        int before = stock("IO-AICO-001");
        var request = new CreateOrderRequest(null, null, null,
                List.of(new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), 3, null)));
        OrderResponse order = orders.create(request, OrderChannel.ADMIN_MANUAL);
        assertThat(stock("IO-AICO-001")).isEqualTo(before - 3);

        assertThatThrownBy(() -> orders.updateStatus(order.id(), OrderStatus.COMPLETED))
                .isInstanceOf(InvalidOrderException.class);

        assertThat(orders.updateStatus(order.id(), OrderStatus.CANCELLED).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stock("IO-AICO-001")).isEqualTo(before);

        assertThatThrownBy(() -> orders.updateStatus(order.id(), OrderStatus.CONFIRMED))
                .isInstanceOf(InvalidOrderException.class);
        assertThat(orders.get(order.id()).items()).hasSize(1);
    }

    @Test
    void complaintsAndDashboard() {
        var created = complaints.create(new ComplaintRequest("Jane Doe", OffsetDateTime.now().minusDays(1),
                "Jigsaw", "Blade snapped on first use"));
        assertThat(created.status()).isEqualTo(ComplaintStatus.PENDING);
        assertThat(complaints.list(ComplaintStatus.PENDING, PageRequest.of(0, 10)).getContent())
                .extracting("id").contains(created.id());
        assertThat(complaints.updateStatus(created.id(), ComplaintStatus.RESOLVED).status())
                .isEqualTo(ComplaintStatus.RESOLVED);

        var order = orders.create(new CreateOrderRequest(null, null, null,
                List.of(new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), 2, null))), OrderChannel.AGENT_CHAT);
        orders.updateStatus(order.id(), OrderStatus.IN_PROGRESS);
        orders.updateStatus(order.id(), OrderStatus.COMPLETED);
        em.flush();

        var kpis = dashboard.kpis();
        assertThat(kpis.completedOrders()).isEqualTo(1);
        assertThat(kpis.agentChatOrders()).isEqualTo(1);
        assertThat(kpis.completedRevenue()).isEqualByComparingTo("579.98");
        assertThat(kpis.pendingComplaints()).isZero();
        assertThat(kpis.topProducts()).hasSize(1);
        assertThat(kpis.topProducts().get(0).unitsSold()).isEqualTo(2L);
    }
}
