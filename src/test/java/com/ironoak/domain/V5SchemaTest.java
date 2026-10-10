package com.ironoak.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The checks V4-V6 put in the database itself, independent of the Java code. Not @Transactional:
 * a failed statement aborts a PostgreSQL transaction, so each statement commits on its own.
 */
@SpringBootTest
@Testcontainers
class V5SchemaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    private long order(String number, String status, String subtotal, String shipping, String grand, boolean hold) {
        return jdbc.queryForObject("""
                INSERT INTO customer_order (order_number, order_status, channel, subtotal, shipping_cost, grand_total,
                                            reservation_expires_at)
                VALUES (?, ?::order_status, 'WEB_CHECKOUT', ?::numeric, ?::numeric, ?::numeric,
                        CASE WHEN ? THEN now() + interval '30 minutes' END)
                RETURNING id""", Long.class, number, status, subtotal, shipping, grand, hold);
    }

    private void violates(String constraint, Runnable statement) {
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @Test
    void enumsCarryTheNewLifecycleValuesAndKeepTheLegacyOnes() {
        assertThat(jdbc.queryForList("select unnest(enum_range(null::order_status))::text", String.class))
                .contains("PENDING_PAYMENT", "PAYMENT_FAILED", "EXPIRED", "DRAFT", "CONFIRMED");
        assertThat(jdbc.queryForList("select unnest(enum_range(null::order_channel))::text", String.class))
                .contains("WEB_CHECKOUT", "PIPER", "ADMIN_MANUAL", "AGENT_CHAT");
        assertThat(jdbc.queryForList("select unnest(enum_range(null::payment_status))::text", String.class))
                .contains("PENDING", "PROCESSING", "PAID", "FAILED", "EXPIRED", "CANCELLED", "REFUNDED");
        assertThat(jdbc.queryForObject("select column_default from information_schema.columns "
                + "where table_name = 'customer_order' and column_name = 'channel'", String.class)).contains("WEB_CHECKOUT");
    }

    @Test
    void orderTotalsMustAddUpAndUnpaidOrdersMustSayWhenTheirHoldEnds() {
        assertThatCode(() -> order("IO-S-1", "PENDING_PAYMENT", "100.00", "5.00", "105.00", true)).doesNotThrowAnyException();
        violates("chk_customer_order_amounts", () -> order("IO-S-2", "CONFIRMED", "100.00", "5.00", "100.00", false));
        violates("chk_customer_order_reservation", () -> order("IO-S-3", "PENDING_PAYMENT", "10.00", "0", "10.00", false));
        violates("uq_customer_order_number", () -> order("IO-S-1", "CONFIRMED", "1.00", "0", "1.00", false));
        violates("chk_customer_order_currency", () -> jdbc.update(
                "update customer_order set currency = 'usd' where order_number = 'IO-S-1'"));
    }

    @Test
    void orderLinesKeepASnapshotAndTheirSubtotalIsPriceTimesQuantity() {
        long orderId = order("IO-S-LINES", "CONFIRMED", "20.00", "0", "20.00", false);
        String insert = """
                INSERT INTO order_item (order_id, item_type, product_id, item_name, item_code, quantity, price, subtotal)
                SELECT ?, 'PRODUCT', id, ?, sku, ?, ?::numeric, ?::numeric FROM product WHERE sku = 'IO-AICO-001'""";
        assertThatCode(() -> jdbc.update(insert, orderId, "Compressor", 2, "10.00", "20.00")).doesNotThrowAnyException();
        violates("chk_order_item_subtotal", () -> jdbc.update(insert, orderId, "Compressor", 2, "10.00", "25.00"));
        violates("item_name", () -> jdbc.update(insert, orderId, null, 1, "10.00", "10.00"));
    }

    @Test
    void stripeIdsAndWebhookEventsAreUnique() {
        long orderId = order("IO-S-PAY", "PENDING_PAYMENT", "50.00", "0", "50.00", true);
        String payment = "INSERT INTO payment (order_id, amount, provider, checkout_session_id, payment_intent_id) "
                + "VALUES (?, 50.00, 'stripe', ?, ?)";
        jdbc.update(payment, orderId, "cs_1", "pi_1");
        violates("uq_payment_checkout_session", () -> jdbc.update(payment, orderId, "cs_1", "pi_2"));
        violates("uq_payment_intent", () -> jdbc.update(payment, orderId, "cs_2", "pi_1"));
        violates("chk_payment_currency", () -> jdbc.update("update payment set currency = 'US' where checkout_session_id = 'cs_1'"));

        String event = "INSERT INTO payment_webhook_event (provider_event_id, event_type, processing_status) VALUES (?, 'x', ?)";
        jdbc.update(event, "evt_1", "RECEIVED");
        violates("payment_webhook_event_provider_event_id_key", () -> jdbc.update(event, "evt_1", "RECEIVED"));
        violates("chk_webhook_processing_status", () -> jdbc.update(event, "evt_2", "DONE"));
    }

    @Test
    void shippingDimensionsAreOptionalButMustBePositive() {
        assertThat(jdbc.queryForObject("select count(*) from product where weight_kg is null", Integer.class)).isEqualTo(87);
        assertThatCode(() -> jdbc.update("update product set weight_kg = 2.5, volume_m3 = 0.01 where sku = 'IO-AICO-001'"))
                .doesNotThrowAnyException();
        violates("chk_product_shipping_dimensions", () -> jdbc.update("update product set weight_kg = 0 where sku = 'IO-AICO-001'"));
        violates("chk_machine_shipping_dimensions", () -> jdbc.update("update milling_machine set volume_m3 = -1"));
        assertThat(List.of(jdbc.queryForObject("select count(*) from milling_machine where weight_kg is null", Integer.class)))
                .containsExactly(4);
    }
}
