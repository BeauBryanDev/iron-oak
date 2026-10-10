package com.ironoak.domain;

import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.TicketStatus;
import com.ironoak.mapper.BookingMapper;
import com.ironoak.mapper.PaymentMapper;
import com.ironoak.mapper.SupportTicketMapper;
import com.ironoak.mapper.WarrantyClaimMapper;
import jakarta.persistence.EntityManager;
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

/** Maps freshly loaded V3 entities (lazy relations and all) inside a transaction. */
@SpringBootTest
@Testcontainers
@Transactional
class V3MapperTest {

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
    private BookingMapper bookingMapper;
    @Autowired
    private WarrantyClaimMapper claimMapper;
    @Autowired
    private PaymentMapper paymentMapper;
    @Autowired
    private SupportTicketMapper ticketMapper;
    @Autowired
    private EntityManager em;

    @Test
    void mapsBookingClaimPaymentAndTicket() {
        Customer customer = new Customer("Jane Doe", "jane@x.com", null);
        em.persist(customer);
        ServiceOffering service = em.createQuery(
                "select s from ServiceOffering s where s.code = 'PREVENTIVE_MAINTENANCE'", ServiceOffering.class)
                .getSingleResult();
        Product product = em.createQuery("select p from Product p where p.sku = 'IO-AICO-001'", Product.class)
                .getSingleResult();

        CustomerOrder order = new CustomerOrder(customer, OrderChannel.ADMIN_MANUAL);
        order.addItem(OrderItem.ofProduct(product, 1));
        em.persist(order);

        ServiceBooking booking = new ServiceBooking(customer, service, "Plant 1, Bay 2",
                OffsetDateTime.now().plusDays(2), "VMC-650", "Noisy spindle");
        booking.setOrder(order);
        em.persist(booking);

        em.flush();
        Long itemId = ((Number) em.createNativeQuery("select id from order_item where order_id = ?1")
                .setParameter(1, order.getId()).getSingleResult()).longValue();
        WarrantyClaim claim = new WarrantyClaim(customer, order, itemId, "Motor died");
        em.persist(claim);
        Payment payment = new Payment(order, new BigDecimal("289.99"), "stripe", "pi_1");
        em.persist(payment);
        SupportTicket guestTicket = new SupportTicket(null, "guest@x.com", null, "Billing", "Charged twice");
        em.persist(guestTicket);
        ChatSession session = new ChatSession(customer);
        em.persist(session);
        SupportTicket customerTicket = new SupportTicket(customer, null, session, "Warranty", "Escalated");
        em.persist(customerTicket);
        em.flush();
        em.clear();

        var bookingDto = bookingMapper.toResponse(em.find(ServiceBooking.class, booking.getId()));
        assertThat(bookingDto.serviceCode()).isEqualTo("PREVENTIVE_MAINTENANCE");
        assertThat(bookingDto.serviceName()).isEqualTo("Preventive Maintenance");
        assertThat(bookingDto.customerName()).isEqualTo("Jane Doe");
        assertThat(bookingDto.orderId()).isEqualTo(order.getId());
        assertThat(bookingDto.cancelReason()).isNull();

        var claimDto = claimMapper.toResponse(em.find(WarrantyClaim.class, claim.getId()));
        assertThat(claimDto.status()).isEqualTo(ClaimStatus.OPEN);
        assertThat(claimDto.customerName()).isEqualTo("Jane Doe");
        assertThat(claimDto.orderId()).isEqualTo(order.getId());
        assertThat(claimDto.orderItemId()).isEqualTo(itemId);

        var paymentDto = paymentMapper.toResponse(em.find(Payment.class, payment.getId()));
        assertThat(paymentDto.orderId()).isEqualTo(order.getId());
        assertThat(paymentDto.amount()).isEqualByComparingTo("289.99");
        assertThat(paymentDto.paidAt()).isNull();

        var guest = ticketMapper.toResponse(em.find(SupportTicket.class, guestTicket.getId()));
        assertThat(guest.customerName()).isNull();
        assertThat(guest.chatSessionId()).isNull();
        assertThat(guest.customerEmail()).isEqualTo("guest@x.com");
        assertThat(guest.status()).isEqualTo(TicketStatus.OPEN);

        var known = ticketMapper.toResponse(em.find(SupportTicket.class, customerTicket.getId()));
        assertThat(known.customerName()).isEqualTo("Jane Doe");
        assertThat(known.chatSessionId()).isEqualTo(session.getId());

        assertThat(bookingMapper.toResponses(java.util.List.of(em.find(ServiceBooking.class, booking.getId())))).hasSize(1);
    }
}
