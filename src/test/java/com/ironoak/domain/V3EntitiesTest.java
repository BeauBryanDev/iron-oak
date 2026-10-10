package com.ironoak.domain;

import com.ironoak.domain.enums.BookingStatus;
import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.PaymentStatus;
import com.ironoak.domain.enums.TicketStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The entities for the V3 tables map correctly and the V3 constraints hold through Hibernate. */
@SpringBootTest
@Testcontainers
@Transactional
class V3EntitiesTest {

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
    private EntityManager em;

    private Customer customer(String email) {
        Customer customer = new Customer("Jane Doe", email, "555-0100");
        customer.setAddress("12 Forge Lane");
        em.persist(customer);
        return customer;
    }

    private CustomerOrder order(Customer customer) {
        CustomerOrder order = new CustomerOrder(customer, OrderChannel.ADMIN_MANUAL);
        em.persist(order);
        return order;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void customerEmailIsStoredLowercaseAndUnique() {
        Customer jane = customer("  Jane@Example.COM ");
        flushAndClear();
        assertThat(em.find(Customer.class, jane.getId()).getEmail()).isEqualTo("jane@example.com");
        assertThat(em.find(Customer.class, jane.getId()).getAddress()).isEqualTo("12 Forge Lane");

        // blank emails become null, so any number of them can coexist
        em.persist(new Customer("No Email 1", " ", null));
        em.persist(new Customer("No Email 2", null, null));
        flushAndClear();

        assertThatThrownBy(() -> {
            em.persist(new Customer("Dup", "JANE@example.com", null));
            em.flush();
        }).isInstanceOf(PersistenceException.class).hasStackTraceContaining("uq_customer_email");
    }

    @Test
    void orderIdempotencyKeyIsUnique() {
        CustomerOrder first = order(null);
        first.setIdempotencyKey("key-1");
        flushAndClear();
        assertThat(em.find(CustomerOrder.class, first.getId()).getIdempotencyKey()).isEqualTo("key-1");

        assertThatThrownBy(() -> {
            CustomerOrder second = new CustomerOrder(null, OrderChannel.ADMIN_MANUAL);
            second.setIdempotencyKey("key-1");
            em.persist(second);
            em.flush();
        }).isInstanceOf(PersistenceException.class).hasStackTraceContaining("idempotency_key");
    }

    @Test
    void serviceBookingLifecycle() {
        Customer customer = customer("a@x.com");
        ServiceOffering service = em.createQuery(
                "select s from ServiceOffering s where s.code = 'PREVENTIVE_MAINTENANCE'", ServiceOffering.class)
                .getSingleResult();
        OffsetDateTime when = OffsetDateTime.now().plusDays(3);

        ServiceBooking booking = new ServiceBooking(customer, service, "Plant 4, Bay 2", when, "VMC-650", "Noisy spindle");
        em.persist(booking);
        flushAndClear();

        ServiceBooking loaded = em.find(ServiceBooking.class, booking.getId());
        assertThat(loaded.getStatus()).isEqualTo(BookingStatus.REQUESTED);
        assertThat(loaded.getCreatedAt()).isNotNull();

        loaded.reschedule(when.plusDays(1));
        loaded.cancel("Customer moved the job");
        flushAndClear();

        ServiceBooking cancelled = em.find(ServiceBooking.class, booking.getId());
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo("Customer moved the job");
        assertThat(cancelled.getUpdatedAt()).isAfterOrEqualTo(cancelled.getCreatedAt());
    }

    @Test
    void warrantyClaimIsTiedToItsOrderAndAllowsOneActiveClaimPerItem() {
        Customer customer = customer("b@x.com");
        CustomerOrder order = order(customer);
        Product product = em.createQuery("select p from Product p where p.sku = 'IO-AICO-001'", Product.class)
                .getSingleResult();
        order.addItem(OrderItem.ofProduct(product, 1));
        flushAndClear();

        Long orderId = order.getId();
        Long itemId = ((Number) em.createNativeQuery("select id from order_item where order_id = ?1")
                .setParameter(1, orderId).getSingleResult()).longValue();
        CustomerOrder managedOrder = em.find(CustomerOrder.class, orderId);
        Customer managedCustomer = managedOrder.getCustomer();

        WarrantyClaim claim = new WarrantyClaim(managedCustomer, managedOrder, itemId, "Motor stopped after a week");
        em.persist(claim);
        flushAndClear();
        assertThat(em.find(WarrantyClaim.class, claim.getId()).getStatus()).isEqualTo(ClaimStatus.OPEN);

        assertThatThrownBy(() -> {
            em.persist(new WarrantyClaim(em.find(Customer.class, managedCustomer.getId()),
                    em.find(CustomerOrder.class, orderId), itemId, "Second claim, same item"));
            em.flush();
        }).isInstanceOf(PersistenceException.class).hasStackTraceContaining("uq_warranty_claim_active_item");
    }

    @Test
    void warrantyClaimRejectsAnItemFromAnotherOrder() {
        Customer customer = customer("c@x.com");
        CustomerOrder orderA = order(customer);
        CustomerOrder orderB = order(customer);
        Product product = em.createQuery("select p from Product p where p.sku = 'IO-AICO-001'", Product.class)
                .getSingleResult();
        orderA.addItem(OrderItem.ofProduct(product, 1));
        flushAndClear();
        Long itemOfA = ((Number) em.createNativeQuery("select id from order_item where order_id = ?1")
                .setParameter(1, orderA.getId()).getSingleResult()).longValue();

        assertThatThrownBy(() -> {
            em.persist(new WarrantyClaim(em.find(Customer.class, customer.getId()),
                    em.find(CustomerOrder.class, orderB.getId()), itemOfA, "Wrong order"));
            em.flush();
        }).isInstanceOf(PersistenceException.class).hasStackTraceContaining("fk_claim_order_item");
    }

    @Test
    void paymentAndSupportTicket() {
        CustomerOrder order = order(customer("d@x.com"));
        Payment payment = new Payment(order, new BigDecimal("579.98"), "stripe", "pi_123");
        em.persist(payment);
        SupportTicket ticket = new SupportTicket(null, "guest@x.com", null,
                "Billing dispute", "Customer was charged twice");
        em.persist(ticket);
        flushAndClear();

        Payment loaded = em.find(Payment.class, payment.getId());
        assertThat(loaded.getStatus()).isEqualTo(PaymentStatus.PENDING);
        loaded.markPaid();
        flushAndClear();
        assertThat(em.find(Payment.class, payment.getId()).getPaidAt()).isNotNull();

        assertThat(em.find(SupportTicket.class, ticket.getId()).getStatus()).isEqualTo(TicketStatus.OPEN);

        assertThatThrownBy(() -> {
            em.persist(new Payment(em.find(CustomerOrder.class, order.getId()),
                    new BigDecimal("1.00"), "stripe", "pi_123"));
            em.flush();
        }).isInstanceOf(PersistenceException.class).hasStackTraceContaining("uq_payment_provider_ref");
    }
}
