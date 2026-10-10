package com.ironoak.domain;

import com.ironoak.domain.enums.BookingStatus;
import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.PaymentStatus;
import com.ironoak.domain.enums.TicketStatus;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.CustomerRepository;
import com.ironoak.repository.PaymentRepository;
import com.ironoak.repository.ServiceBookingRepository;
import com.ironoak.repository.ServiceOfferingRepository;
import com.ironoak.repository.SupportTicketRepository;
import com.ironoak.repository.WarrantyClaimRepository;
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

@SpringBootTest
@Testcontainers
@Transactional
class V3RepositoriesTest {

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
    private ServiceBookingRepository bookings;
    @Autowired
    private WarrantyClaimRepository claims;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private SupportTicketRepository tickets;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private CustomerOrderRepository orders;
    @Autowired
    private ServiceOfferingRepository serviceOfferings;
    @Autowired
    private EntityManager em;

    @Test
    void bookingQueries() {
        Customer customer = customers.save(new Customer("Jane Doe", "jane@x.com", null));
        ServiceOffering service = serviceOfferings.findByCode("PREVENTIVE_MAINTENANCE").orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();

        ServiceBooking soon = bookings.save(new ServiceBooking(customer, service, "Plant 1", now.plusDays(1), null, null));
        ServiceBooking later = bookings.save(new ServiceBooking(customer, service, "Plant 2", now.plusDays(5), null, null));
        ServiceBooking cancelled = bookings.save(new ServiceBooking(customer, service, "Plant 3", now.plusDays(2), null, null));
        cancelled.cancel("Changed plans");
        em.flush();
        em.clear();

        assertThat(bookings.findByCustomerIdOrderByScheduledAtDesc(customer.getId()))
                .extracting(ServiceBooking::getId).containsExactly(later.getId(), cancelled.getId(), soon.getId());

        var queue = bookings.findByStatusInOrderByScheduledAtAsc(
                List.of(BookingStatus.REQUESTED, BookingStatus.CONFIRMED), PageRequest.of(0, 10));
        assertThat(queue.getContent()).extracting(ServiceBooking::getId).containsExactly(soon.getId(), later.getId());
        assertThat(queue.getContent().get(0).getCustomer().getName()).isEqualTo("Jane Doe");

        assertThat(bookings.findWithDetailsById(soon.getId()).orElseThrow().getServiceOffering().getCode())
                .isEqualTo("PREVENTIVE_MAINTENANCE");
        assertThat(bookings.countByStatus(BookingStatus.CANCELLED)).isEqualTo(1);
    }

    @Test
    void claimQueries() {
        Customer customer = customers.save(new Customer("Jane Doe", "claim@x.com", null));
        CustomerOrder order = orders.save(new CustomerOrder(customer, OrderChannel.ADMIN_MANUAL));
        Product product = em.createQuery("select p from Product p where p.sku = 'IO-AICO-001'", Product.class)
                .getSingleResult();
        order.addItem(OrderItem.ofProduct(product, 1));
        orders.saveAndFlush(order);
        Long itemId = ((Number) em.createNativeQuery("select id from order_item where order_id = ?1")
                .setParameter(1, order.getId()).getSingleResult()).longValue();

        List<ClaimStatus> active = List.of(ClaimStatus.OPEN, ClaimStatus.IN_REVIEW);
        assertThat(claims.existsByOrderItemIdAndStatusIn(itemId, active)).isFalse();

        WarrantyClaim claim = claims.saveAndFlush(new WarrantyClaim(customer, order, itemId, "Motor died"));
        assertThat(claims.existsByOrderItemIdAndStatusIn(itemId, active)).isTrue();
        assertThat(claims.findByOrderId(order.getId())).hasSize(1);
        assertThat(claims.findByCustomerIdOrderByCreatedAtDesc(customer.getId())).hasSize(1);
        assertThat(claims.findByStatus(ClaimStatus.OPEN, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);

        claim.resolve(ClaimStatus.APPROVED, "Replacement shipped");
        claims.flush();
        assertThat(claims.existsByOrderItemIdAndStatusIn(itemId, active)).isFalse();
        assertThat(claims.countByStatus(ClaimStatus.APPROVED)).isEqualTo(1);
    }

    @Test
    void paymentQueries() {
        CustomerOrder order = orders.save(new CustomerOrder(null, OrderChannel.ADMIN_MANUAL));
        Payment paid = payments.save(new Payment(order, new BigDecimal("100.50"), "stripe", "pi_1"));
        Payment failed = payments.save(new Payment(order, new BigDecimal("40.00"), "stripe", "pi_2"));
        Payment alsoPaid = payments.save(new Payment(order, new BigDecimal("20.00"), "stripe", "pi_3"));
        paid.markPaid();
        alsoPaid.markPaid();
        failed.markFailed();
        payments.flush();
        em.clear();

        assertThat(payments.findByProviderAndProviderReference("stripe", "pi_2")).isPresent();
        assertThat(payments.findByProviderAndProviderReference("stripe", "nope")).isEmpty();
        assertThat(payments.findByOrderIdOrderByCreatedAtDesc(order.getId())).hasSize(3);
        assertThat(payments.sumAmountByOrderAndStatus(order.getId(), PaymentStatus.PAID))
                .isEqualByComparingTo("120.50");
        assertThat(payments.sumAmountByOrderAndStatus(order.getId(), PaymentStatus.PENDING))
                .isEqualByComparingTo("0");
    }

    @Test
    void ticketQueries() {
        Customer customer = customers.save(new Customer("Jane Doe", "t@x.com", null));
        ChatSession session = new ChatSession(customer);
        em.persist(session);
        tickets.save(new SupportTicket(customer, null, session, "Billing", "Charged twice"));
        SupportTicket closed = tickets.save(new SupportTicket(null, "guest@x.com", null, "Other", "Question"));
        closed.setStatus(TicketStatus.CLOSED);
        tickets.flush();

        assertThat(tickets.findByStatus(TicketStatus.OPEN, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        assertThat(tickets.findByCustomerIdOrderByCreatedAtDesc(customer.getId())).hasSize(1);
        assertThat(tickets.findByChatSessionId(session.getId())).hasSize(1);
        assertThat(tickets.countByStatus(TicketStatus.CLOSED)).isEqualTo(1);
    }
}
