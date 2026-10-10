package com.ironoak.services;

import com.ironoak.TestOrders;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.dto.request.CreateOrderRequest.Item;
import com.ironoak.dto.request.CreatePaymentRequest;
import com.ironoak.dto.response.OrderResponse;
import com.ironoak.exceptions.InvalidOrderException;
import com.ironoak.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The stock hold of unpaid orders: the expiry job, and what it must leave alone. Not
 * @Transactional, because the job commits each order in its own transaction.
 */
@SpringBootTest
@Testcontainers
class ReservationExpiryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.stripe.api-key", () -> "");
    }

    @Autowired
    private OrderService orders;
    @Autowired
    private PaymentService payments;
    @Autowired
    private ProductRepository products;
    @Autowired
    private JdbcTemplate jdbc;

    private long productId() {
        return products.findBySku("IO-AICO-001").orElseThrow().getId();
    }

    private int stock() {
        return jdbc.queryForObject("select stock_quantity from product where sku = 'IO-AICO-001'", Integer.class);
    }

    private OrderResponse unpaidOrder(int quantity) {
        return orders.create(TestOrders.web("Hold Holder", "hold@example.com",
                List.of(new Item(OrderItemType.PRODUCT, productId(), quantity, null))), OrderChannel.WEB_CHECKOUT);
    }

    private void ageHold(long orderId) {
        jdbc.update("update customer_order set reservation_expires_at = now() - interval '1 minute' where id = ?", orderId);
    }

    private String status(long orderId) {
        return jdbc.queryForObject("select order_status::text from customer_order where id = ?", String.class, orderId);
    }

    @Test
    void unpaidOrdersPastTheirHoldExpireAndGiveTheirStockBackOnce() {
        int before = stock();
        OrderResponse due = unpaidOrder(2);
        OrderResponse notDue = unpaidOrder(1);
        assertThat(stock()).isEqualTo(before - 3);
        var pending = payments.create(new CreatePaymentRequest(due.id(), "manual", "ref-due"));
        ageHold(due.id());

        orders.expireStaleReservations();

        assertThat(status(due.id())).isEqualTo("EXPIRED");
        assertThat(status(notDue.id())).isEqualTo("PENDING_PAYMENT");
        assertThat(stock()).isEqualTo(before - 1);
        assertThat(jdbc.queryForObject("select stock_reserved from customer_order where id = ?", Boolean.class, due.id())).isFalse();
        assertThat(payments.get(pending.id()).status().name()).isEqualTo("EXPIRED");

        // running again (or cancelling afterwards) never returns the stock a second time
        orders.expireStaleReservations();
        assertThat(stock()).isEqualTo(before - 1);
        assertThatThrownBy(() -> orders.updateStatus(due.id(), OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderException.class);
        assertThat(stock()).isEqualTo(before - 1);
        assertThatThrownBy(() -> payments.create(new CreatePaymentRequest(due.id(), "manual", "ref-too-late")))
                .hasMessageContaining("cannot be paid");
    }

    @Test
    void anOrderWithAPaymentInFlightIsNotExpired() {
        OrderResponse order = unpaidOrder(1);
        var payment = payments.create(new CreatePaymentRequest(order.id(), "manual", "ref-flight"));
        payments.updateStatus(payment.id(), com.ironoak.domain.enums.PaymentStatus.PROCESSING);
        ageHold(order.id());

        orders.expireStaleReservations();

        assertThat(status(order.id())).isEqualTo("PENDING_PAYMENT");
        // once the money arrives the order is confirmed and the hold no longer matters
        payments.updateStatus(payment.id(), com.ironoak.domain.enums.PaymentStatus.PAID);
        assertThat(status(order.id())).isEqualTo("CONFIRMED");
        orders.expireStaleReservations();
        assertThat(status(order.id())).isEqualTo("CONFIRMED");
    }

    @Test
    void cancellingAnUnpaidOrderReturnsItsStockAndStaffCannotConfirmItByHand() {
        int before = stock();
        OrderResponse order = unpaidOrder(3);
        assertThatThrownBy(() -> orders.updateStatus(order.id(), OrderStatus.CONFIRMED))
                .isInstanceOf(InvalidOrderException.class);
        orders.updateStatus(order.id(), OrderStatus.CANCELLED);
        assertThat(stock()).isEqualTo(before);
        orders.expireStaleReservations();
        assertThat(stock()).isEqualTo(before);
    }
}
