package com.ironoak.domain;

import com.ironoak.domain.enums.PaymentStatus;
import com.ironoak.dto.request.CreatePaymentRequest;
import com.ironoak.services.PaymentService;
import com.ironoak.TestOrders;
import com.ironoak.dto.request.ComplaintFilter;
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
    private PaymentService payments;
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
        var request = TestOrders.web("Jane Doe", "Jane@Example.com", List.of(
                new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), 2, null),
                new Item(OrderItemType.SERVICE, serviceId("PREVENTIVE_MAINTENANCE"), 1, null),
                new Item(OrderItemType.SERVICE, serviceId("EMERGENCY_REPAIR"), 1, new BigDecimal("3.0")),
                new Item(OrderItemType.MACHINE, machineId("BM-200"), 1, null)));

        OrderResponse order = orders.create(request, OrderChannel.WEB_CHECKOUT);

        // 2 x 289.99 + 240.00 + (180.00 x 3.0 = 540.00) + 4250.00
        assertThat(order.subtotal()).isEqualByComparingTo("5609.98");
        // Bogota to Bogota: domestic base fees only (tools 2.50 + machines 48.00); no weights seeded yet
        assertThat(order.shippingCost()).isEqualByComparingTo("50.50");
        assertThat(order.taxes()).isEqualByComparingTo("0");
        assertThat(order.grandTotal()).isEqualByComparingTo("5660.48");
        assertThat(order.currency()).isEqualTo("USD");
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.channel()).isEqualTo(OrderChannel.WEB_CHECKOUT);
        assertThat(order.orderNumber()).matches("IO-\\d{8}-[A-Z2-9]{8}");
        assertThat(order.reservationExpiresAt()).isAfter(OffsetDateTime.now());
        assertThat(order.customerName()).isEqualTo("Jane Doe");
        assertThat(order.customerEmail()).isEqualTo("jane@example.com");
        assertThat(order.items()).hasSize(4);
        assertThat(order.items()).extracting("itemCode")
                .containsExactlyInAnyOrder("IO-AICO-001", "PREVENTIVE_MAINTENANCE", "EMERGENCY_REPAIR", "BM-200");
        assertThat(stock("IO-AICO-001")).isEqualTo(before - 2);
    }

    @Test
    void webOrdersNeedAContactAndAnAddressForGoods() {
        var product = List.of(new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), 1, null));
        assertThatThrownBy(() -> orders.create(TestOrders.staff(product), OrderChannel.WEB_CHECKOUT))
                .isInstanceOf(InvalidOrderException.class).hasMessageContaining("customerEmail");
        var noAddress = new CreateOrderRequest("Jane", "jane@example.com", null, null, null, null, null, product);
        assertThatThrownBy(() -> orders.create(noAddress, OrderChannel.WEB_CHECKOUT))
                .isInstanceOf(InvalidOrderException.class).hasMessageContaining("shippingAddress");
        var abroad = new CreateOrderRequest("Jane", "jane@example.com", null, "FR", null, "Paris", "Rue 1", product);
        assertThatThrownBy(() -> orders.create(abroad, OrderChannel.WEB_CHECKOUT))
                .isInstanceOf(InvalidOrderException.class).hasMessageContaining("deliver");

        // services are not shipped, so no address is needed
        var service = List.of(new Item(OrderItemType.SERVICE, serviceId("PREVENTIVE_MAINTENANCE"), 1, null));
        var serviceOnly = new CreateOrderRequest("Jane", "jane@example.com", null, null, null, null, null, service);
        assertThat(orders.create(serviceOnly, OrderChannel.WEB_CHECKOUT).shippingCost()).isEqualByComparingTo("0");

        // staff orders need neither and start confirmed
        OrderResponse staff = orders.create(TestOrders.staff(product), OrderChannel.ADMIN_MANUAL);
        assertThat(staff.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(staff.reservationExpiresAt()).isNull();
    }

    @Test
    void outOfStockRejectsTheWholeOrder() {
        int before = stock("IO-AICO-001");
        var request = TestOrders.web("Jane Doe", "jane@example.com", List.of(
                new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), 1, null),
                new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), before + 5, null)));

        assertThatThrownBy(() -> orders.create(request, OrderChannel.WEB_CHECKOUT))
                .isInstanceOf(OutOfStockException.class);
    }

    @Test
    void rejectsQuoteOnlyAndBadHourlyServices() {
        var quote = TestOrders.staff(List.of(new Item(OrderItemType.SERVICE, serviceId("SPINDLE_TOOLING_SERVICE"), 1, null)));
        assertThatThrownBy(() -> orders.create(quote, OrderChannel.ADMIN_MANUAL))
                .isInstanceOf(InvalidOrderException.class).hasMessageContaining("quote");

        var noHours = TestOrders.staff(List.of(new Item(OrderItemType.SERVICE, serviceId("EMERGENCY_REPAIR"), 1, null)));
        assertThatThrownBy(() -> orders.create(noHours, OrderChannel.ADMIN_MANUAL))
                .isInstanceOf(InvalidOrderException.class);

        var tooMany = TestOrders.staff(List.of(new Item(OrderItemType.SERVICE, serviceId("EMERGENCY_REPAIR"), 1, new BigDecimal("9"))));
        assertThatThrownBy(() -> orders.create(tooMany, OrderChannel.ADMIN_MANUAL))
                .isInstanceOf(InvalidOrderException.class);

        var missing = TestOrders.staff(List.of(new Item(OrderItemType.PRODUCT, 999999L, 1, null)));
        assertThatThrownBy(() -> orders.create(missing, OrderChannel.ADMIN_MANUAL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void statusTransitionsAndCancelRestoresStock() {
        int before = stock("IO-AICO-001");
        var request = TestOrders.staff(List.of(new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), 3, null)));
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
        assertThat(complaints.list(new ComplaintFilter(List.of(ComplaintStatus.PENDING), null, null, null), PageRequest.of(0, 10)).getContent())
                .extracting("id").contains(created.id());
        assertThat(complaints.updateStatus(created.id(), ComplaintStatus.RESOLVED).status())
                .isEqualTo(ComplaintStatus.RESOLVED);

        var order = orders.create(TestOrders.web("Jane Doe", "jane@example.com",
                List.of(new Item(OrderItemType.PRODUCT, productId("IO-AICO-001"), 2, null))), OrderChannel.WEB_CHECKOUT);
        assertThat(dashboard.kpis().pendingPaymentOrders()).isEqualTo(1);
        var payment = payments.create(new CreatePaymentRequest(order.id(), "manual", "bank-transfer-1"));
        payments.updateStatus(payment.id(), PaymentStatus.PAID); // a full payment confirms the order
        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.CONFIRMED);
        orders.updateStatus(order.id(), OrderStatus.IN_PROGRESS);
        orders.updateStatus(order.id(), OrderStatus.COMPLETED);
        em.flush();

        var kpis = dashboard.kpis();
        assertThat(kpis.completedOrders()).isEqualTo(1);
        assertThat(kpis.webCheckoutOrders()).isEqualTo(1);
        assertThat(kpis.piperOrders()).isZero();
        assertThat(kpis.pendingPaymentOrders()).isZero();
        // 2 x 289.99 + 2.50 domestic tools shipping
        assertThat(kpis.completedRevenue()).isEqualByComparingTo("582.48");
        assertThat(kpis.pendingComplaints()).isZero();
        assertThat(kpis.topProducts()).hasSize(1);
        assertThat(kpis.topProducts().get(0).unitsSold()).isEqualTo(2L);
    }
}
