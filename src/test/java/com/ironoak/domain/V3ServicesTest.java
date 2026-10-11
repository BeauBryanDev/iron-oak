package com.ironoak.domain;

import com.ironoak.TestOrders;
import com.ironoak.dto.request.SupportTicketFilter;
import com.ironoak.dto.request.WarrantyClaimFilter;
import com.ironoak.dto.request.BookingFilter;
import com.ironoak.domain.enums.BookingStatus;
import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.domain.enums.PaymentStatus;
import com.ironoak.domain.enums.TicketStatus;
import com.ironoak.dto.request.CancelBookingRequest;
import com.ironoak.dto.request.CreateBookingRequest;
import com.ironoak.dto.request.CreateOrderRequest;
import com.ironoak.dto.request.CreateOrderRequest.Item;
import com.ironoak.dto.request.CreatePaymentRequest;
import com.ironoak.dto.request.CreateSupportTicketRequest;
import com.ironoak.dto.request.CreateWarrantyClaimRequest;
import com.ironoak.dto.request.RescheduleBookingRequest;
import com.ironoak.dto.request.UpdateWarrantyClaimStatusRequest;
import com.ironoak.dto.response.OrderResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.CustomerRepository;
import com.ironoak.repository.ProductRepository;
import com.ironoak.repository.ServiceOfferingRepository;
import com.ironoak.services.BookingService;
import com.ironoak.services.DashboardService;
import com.ironoak.services.OrderService;
import com.ironoak.services.PaymentService;
import com.ironoak.services.SupportTicketService;
import com.ironoak.services.WarrantyClaimService;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class V3ServicesTest {

    private static final String EMAIL = "jane@example.com";

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
    private BookingService bookings;
    @Autowired
    private WarrantyClaimService claims;
    @Autowired
    private PaymentService payments;
    @Autowired
    private SupportTicketService tickets;
    @Autowired
    private DashboardService dashboard;
    @Autowired
    private ProductRepository products;
    @Autowired
    private ServiceOfferingRepository serviceOfferings;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private EntityManager em;

    private Long productId() {
        return products.findBySku("IO-AICO-001").orElseThrow().getId();
    }

    private Long serviceId(String code) {
        return serviceOfferings.findByCode(code).orElseThrow().getId();
    }

    private OrderResponse order(Item... items) {
        return orders.create(TestOrders.web("Jane Doe", EMAIL, List.of(items)), OrderChannel.WEB_CHECKOUT);
    }

    /** Pays the order in full (which confirms it), then works it to COMPLETED. */
    private OrderResponse complete(OrderResponse created) {
        var payment = payments.create(new CreatePaymentRequest(created.id(), "manual", "ref-" + created.id()));
        payments.updateStatus(payment.id(), PaymentStatus.PAID);
        orders.updateStatus(created.id(), OrderStatus.IN_PROGRESS);
        return orders.updateStatus(created.id(), OrderStatus.COMPLETED);
    }

    private OrderResponse productOrder(int quantity) {
        return order(new Item(OrderItemType.PRODUCT, productId(), quantity, null));
    }

    private OrderResponse completedProductOrder() {
        return complete(productOrder(1));
    }

    private CreateBookingRequest bookingRequest(String email) {
        return new CreateBookingRequest("Jane Doe", email, null, serviceId("PREVENTIVE_MAINTENANCE"),
                "Plant 1, Bay 2", "CO", "Medellín", OffsetDateTime.now().plusDays(3), "VMC-650", null);
    }

    @Test
    void orderIdempotencyAndCustomerReuse() {
        int before = products.findBySku("IO-AICO-001").orElseThrow().getStockQuantity();
        var request = TestOrders.web("Jane Doe", "Jane@Example.com",
                List.of(new Item(OrderItemType.PRODUCT, productId(), 2, null)));

        OrderResponse first = orders.create(request, OrderChannel.WEB_CHECKOUT, "key-123");
        OrderResponse again = orders.create(request, OrderChannel.WEB_CHECKOUT, "key-123");
        OrderResponse other = orders.create(request, OrderChannel.WEB_CHECKOUT, "key-456");

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(other.id()).isNotEqualTo(first.id());
        // two real orders (4 units), not three
        assertThat(products.findBySku("IO-AICO-001").orElseThrow().getStockQuantity()).isEqualTo(before - 4);
        // one customer row for the email, whatever its case
        assertThat(customers.findAll().stream().filter(c -> EMAIL.equals(c.getEmail()))).hasSize(1);
    }

    @Test
    void bookingLifecycleAndOwnership() {
        var created = bookings.create(bookingRequest("Jane@Example.com"));
        assertThat(created.status()).isEqualTo(BookingStatus.REQUESTED);
        assertThat(created.serviceCode()).isEqualTo("PREVENTIVE_MAINTENANCE");

        assertThat(bookings.get(created.id(), EMAIL).id()).isEqualTo(created.id());
        assertThatThrownBy(() -> bookings.get(created.id(), "other@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(bookings.listMine(EMAIL)).hasSize(1);
        assertThat(bookings.listMine("nobody@example.com")).isEmpty();

        OffsetDateTime later = OffsetDateTime.now().plusDays(10);
        assertThat(bookings.reschedule(created.id(), EMAIL, new RescheduleBookingRequest(later)).scheduledAt())
                .isEqualTo(later);

        assertThat(bookings.queue(new BookingFilter(null, null, null, null, null, null), PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThatThrownBy(() -> bookings.updateStatus(created.id(), BookingStatus.COMPLETED))
                .isInstanceOf(BusinessRuleException.class);

        var cancelled = bookings.cancel(created.id(), EMAIL, new CancelBookingRequest("Plans changed"));
        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo("Plans changed");
        assertThatThrownBy(() -> bookings.cancel(created.id(), EMAIL, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> bookings.reschedule(created.id(), EMAIL, new RescheduleBookingRequest(later)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(bookings.queue(new BookingFilter(null, null, null, null, null, null), PageRequest.of(0, 10)).getContent()).isEmpty();

        var staffFlow = bookings.create(bookingRequest(EMAIL));
        assertThat(bookings.updateStatus(staffFlow.id(), BookingStatus.CONFIRMED).status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(bookings.updateStatus(staffFlow.id(), BookingStatus.COMPLETED).status()).isEqualTo(BookingStatus.COMPLETED);

        var missingService = new CreateBookingRequest("Jane", EMAIL, null, 999999L, "Plant", "CO", "Bogota", OffsetDateTime.now().plusDays(1), null, null);
        assertThatThrownBy(() -> bookings.create(missingService)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void warrantyClaimEligibility() {
        OrderResponse openOrder = productOrder(1);
        Long openItem = openOrder.items().get(0).id();
        assertThatThrownBy(() -> claims.create(new CreateWarrantyClaimRequest(EMAIL, openOrder.id(), openItem, "Broken")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("completed");

        OrderResponse done = completedProductOrder();
        Long item = done.items().get(0).id();

        assertThatThrownBy(() -> claims.create(new CreateWarrantyClaimRequest("other@example.com", done.id(), item, "Broken")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> claims.create(new CreateWarrantyClaimRequest(EMAIL, done.id(), openItem, "Broken")))
                .isInstanceOf(ResourceNotFoundException.class);

        var claim = claims.create(new CreateWarrantyClaimRequest(EMAIL, done.id(), item, "Motor died"));
        assertThat(claim.status()).isEqualTo(ClaimStatus.OPEN);
        assertThat(claims.get(claim.id(), EMAIL).orderItemId()).isEqualTo(item);
        assertThatThrownBy(() -> claims.get(claim.id(), "other@example.com")).isInstanceOf(ResourceNotFoundException.class);
        assertThat(claims.listMine(EMAIL)).hasSize(1);

        assertThatThrownBy(() -> claims.create(new CreateWarrantyClaimRequest(EMAIL, done.id(), item, "Again")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("open claim");
    }

    @Test
    void warrantyClaimRejectsServicesAndExpiredWarranty() {
        OrderResponse serviceOrder = order(new Item(OrderItemType.SERVICE, serviceId("PREVENTIVE_MAINTENANCE"), 1, null));
        complete(serviceOrder);
        assertThatThrownBy(() -> claims.create(new CreateWarrantyClaimRequest(
                EMAIL, serviceOrder.id(), serviceOrder.items().get(0).id(), "Bad service")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Services");

        OrderResponse old = completedProductOrder();
        em.flush();
        em.createNativeQuery("update customer_order set created_at = now() - interval '2 years' where id = ?1")
                .setParameter(1, old.id()).executeUpdate();
        em.clear();
        assertThatThrownBy(() -> claims.create(new CreateWarrantyClaimRequest(
                EMAIL, old.id(), old.items().get(0).id(), "Too late")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("expired");
    }

    @Test
    void warrantyClaimReviewFlowNeedsNotes() {
        OrderResponse done = completedProductOrder();
        var claim = claims.create(new CreateWarrantyClaimRequest(EMAIL, done.id(), done.items().get(0).id(), "Motor died"));

        assertThatThrownBy(() -> claims.updateStatus(claim.id(), new UpdateWarrantyClaimStatusRequest(ClaimStatus.APPROVED, "ok")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(claims.updateStatus(claim.id(), new UpdateWarrantyClaimStatusRequest(ClaimStatus.IN_REVIEW, null)).status())
                .isEqualTo(ClaimStatus.IN_REVIEW);
        assertThatThrownBy(() -> claims.updateStatus(claim.id(), new UpdateWarrantyClaimStatusRequest(ClaimStatus.APPROVED, " ")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("note");

        var approved = claims.updateStatus(claim.id(), new UpdateWarrantyClaimStatusRequest(ClaimStatus.APPROVED, "Replace the unit"));
        assertThat(approved.resolvedAt()).isNull();
        var resolved = claims.updateStatus(claim.id(), new UpdateWarrantyClaimStatusRequest(ClaimStatus.RESOLVED, "Replacement shipped"));
        assertThat(resolved.resolvedAt()).isNotNull();
        assertThat(resolved.resolutionNote()).isEqualTo("Replacement shipped");

        // a finished claim no longer blocks a new one
        var next = claims.create(new CreateWarrantyClaimRequest(EMAIL, done.id(), done.items().get(0).id(), "Broke again"));
        assertThat(next.status()).isEqualTo(ClaimStatus.OPEN);
        assertThat(claims.list(new WarrantyClaimFilter(List.of(ClaimStatus.OPEN), null, null, null), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void paymentsAreDerivedFromTheOrderAndNeverOverpay() {
        OrderResponse order = productOrder(2);

        var first = payments.create(new CreatePaymentRequest(order.id(), "stripe", "pi_1"));
        assertThat(first.amount()).isEqualByComparingTo(order.grandTotal());
        assertThat(first.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.create(new CreatePaymentRequest(order.id(), "stripe", "pi_1")).id()).isEqualTo(first.id());

        OrderResponse another = productOrder(1);
        assertThatThrownBy(() -> payments.create(new CreatePaymentRequest(another.id(), "stripe", "pi_1")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("another order");

        var second = payments.create(new CreatePaymentRequest(order.id(), "stripe", "pi_2"));
        assertThat(payments.updateStatus(first.id(), PaymentStatus.PAID).paidAt()).isNotNull();
        assertThat(orders.get(order.id()).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThatThrownBy(() -> payments.updateStatus(second.id(), PaymentStatus.PAID))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exceed");
        assertThat(payments.updateStatus(second.id(), PaymentStatus.FAILED).status()).isEqualTo(PaymentStatus.FAILED);
        assertThatThrownBy(() -> payments.updateStatus(first.id(), PaymentStatus.FAILED))
                .isInstanceOf(BusinessRuleException.class);

        assertThatThrownBy(() -> payments.create(new CreatePaymentRequest(order.id(), "stripe", "pi_3")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("nothing left");
        assertThat(payments.listForOrder(order.id())).hasSize(2);

        orders.updateStatus(another.id(), OrderStatus.CANCELLED);
        assertThatThrownBy(() -> payments.create(new CreatePaymentRequest(another.id(), "stripe", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be paid");
    }

    @Test
    void supportTicketsLinkCustomersAndFollowTheirFlow() {
        customers.save(new Customer("Jane Doe", EMAIL, null));
        ChatSession session = new ChatSession(null);
        em.persist(session);
        em.flush();

        var known = tickets.create(new CreateSupportTicketRequest("JANE@example.com", session.getId(), "Billing", "Charged twice"));
        assertThat(known.customerName()).isEqualTo("Jane Doe");
        assertThat(known.chatSessionId()).isEqualTo(session.getId());
        assertThat(known.customerEmail()).isEqualTo(EMAIL);

        var guest = tickets.create(new CreateSupportTicketRequest(null, null, "Question", "Do you ship abroad?"));
        assertThat(guest.customerName()).isNull();

        assertThatThrownBy(() -> tickets.create(new CreateSupportTicketRequest(null, 999999L, "x", "y")))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(tickets.updateStatus(known.id(), TicketStatus.IN_PROGRESS).status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(tickets.updateStatus(known.id(), TicketStatus.CLOSED).status()).isEqualTo(TicketStatus.CLOSED);
        assertThatThrownBy(() -> tickets.updateStatus(known.id(), TicketStatus.OPEN)).isInstanceOf(BusinessRuleException.class);
        assertThat(tickets.list(new SupportTicketFilter(List.of(TicketStatus.OPEN), null, null, null), PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void dashboardCountsTheNewQueues() {
        bookings.create(bookingRequest(EMAIL));
        OrderResponse done = completedProductOrder();
        claims.create(new CreateWarrantyClaimRequest(EMAIL, done.id(), done.items().get(0).id(), "Motor died"));
        tickets.create(new CreateSupportTicketRequest(null, null, "Question", "Help"));
        em.flush();

        var kpis = dashboard.kpis();
        assertThat(kpis.requestedBookings()).isEqualTo(1);
        assertThat(kpis.openWarrantyClaims()).isEqualTo(1);
        assertThat(kpis.openSupportTickets()).isEqualTo(1);
    }
}
