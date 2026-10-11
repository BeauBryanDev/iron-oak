package com.ironoak.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.TestOrders;
import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.repository.ProductRepository;
import com.ironoak.security.JwtService;
import com.ironoak.services.StripeGateway;
import com.stripe.Stripe;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkout, webhook and refund end to end against the real schema. Stripe's network calls
 * (create/retrieve session, create refund) are stubbed on a spy of StripeGateway; webhook
 * signature verification is the real one, with a test signing secret. The API key is blank, so
 * any call that was not stubbed fails with 503 instead of reaching Stripe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StripeCheckoutFlowTest {

    private static final String WEBHOOK_SECRET = "whsec_test_checkout_flow_secret";
    private static final String EMAIL = "buyer@example.com";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.stripe.api-key", () -> "");
        registry.add("app.stripe.webhook-secret", () -> WEBHOOK_SECRET);
        registry.add("app.checkout.frontend-base-url", () -> "https://shop.example.test");
        registry.add("app.security.rate-limit.public-write-per-minute", () -> "1000");
        registry.add("app.security.rate-limit.public-lookup-per-minute", () -> "1000");
    }

    @MockitoSpyBean
    private StripeGateway stripe;

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
    private JdbcTemplate jdbc;

    private String bearer;

    @BeforeEach
    void admin() {
        adminUsers.deleteAll();
        adminUsers.save(new AdminUser("owner", "owner@ironoak.test", passwordEncoder.encode("correct-horse"), "Owner"));
        bearer = "Bearer " + jwtService.issueToken("owner");
    }

    // ---- helpers -------------------------------------------------------------------------

    private JsonNode send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : objectMapper.readTree(body);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer);
    }

    /** A web order for one air compressor (289.99) shipped inside Bogota (2.53, a 16.1 km route), plus 19% Colombian tax on the item (55.10): 347.62. */
    private JsonNode placeOrder() throws Exception {
        long productId = products.findBySku("IO-AICO-001").orElseThrow().getId();
        return send(json(post("/api/orders"), TestOrders.webJson("Ada Buyer", EMAIL, "PRODUCT", productId, 1)), 201);
    }

    private static Session session(String id, String status) {
        Session session = new Session();
        session.setId(id);
        session.setStatus(status);
        session.setUrl("https://checkout.stripe.com/c/pay/" + id);
        session.setExpiresAt(Instant.now().plus(31, ChronoUnit.MINUTES).getEpochSecond());
        return session;
    }

    private static Refund refund(String status) {
        Refund refund = new Refund();
        refund.setId("re_" + UUID.randomUUID().toString().substring(0, 8));
        refund.setStatus(status);
        return refund;
    }

    private JsonNode startCheckout(long orderId, String sessionId) throws Exception {
        doReturn(session(sessionId, "open")).when(stripe).createCheckoutSession(any(), anyString());
        return send(json(post("/api/orders/" + orderId + "/checkout-session"), "{\"email\":\"" + EMAIL + "\"}"), 200);
    }

    private static String sessionEvent(String type, JsonNode order, String sessionId, long amountCents,
                                       String paymentStatus, String paymentIntent) {
        return """
                {"id":"evt_%s","object":"event","api_version":"%s","created":%d,"type":"%s",
                 "data":{"object":{"id":"%s","object":"checkout.session","status":"complete",
                   "payment_status":"%s","amount_total":%d,"currency":"usd","payment_intent":"%s",
                   "client_reference_id":"%s","metadata":{"order_id":"%d","order_number":"%s"}}}}
                """.formatted(UUID.randomUUID().toString().replace("-", ""), Stripe.API_VERSION,
                Instant.now().getEpochSecond(), type, sessionId, paymentStatus, amountCents, paymentIntent,
                order.get("orderNumber").asText(), order.get("id").asLong(), order.get("orderNumber").asText());
    }

    private static String chargeRefundedEvent(String paymentIntent, boolean full) {
        return """
                {"id":"evt_%s","object":"event","api_version":"%s","created":%d,"type":"charge.refunded",
                 "data":{"object":{"id":"ch_test","object":"charge","payment_intent":"%s","refunded":%s,
                   "amount":34762,"amount_refunded":%d,"currency":"usd"}}}
                """.formatted(UUID.randomUUID().toString().replace("-", ""), Stripe.API_VERSION,
                Instant.now().getEpochSecond(), paymentIntent, full, full ? 34762 : 1000);
    }

    /** The Stripe-Signature header for this body, made the way Stripe makes it. */
    private static String signature(String payload, String secret, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }

    private int webhook(String payload) throws Exception {
        return mockMvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header("Stripe-Signature", signature(payload, WEBHOOK_SECRET, Instant.now().getEpochSecond())))
                .andReturn().getResponse().getStatus();
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("select order_status::text from customer_order where id = ?", String.class, orderId);
    }

    private String paymentStatus(String sessionId) {
        return jdbc.queryForObject("select status::text from payment where checkout_session_id = ?", String.class, sessionId);
    }

    private long paymentId(String sessionId) {
        return jdbc.queryForObject("select id from payment where checkout_session_id = ?", Long.class, sessionId);
    }

    // ---- starting a payment ----------------------------------------------------------------

    @Test
    void checkoutPageIsBuiltFromTheOrdersOwnFiguresAndReused() throws Exception {
        JsonNode order = placeOrder();
        long orderId = order.get("id").asLong();
        String number = order.get("orderNumber").asText();

        JsonNode page = startCheckout(orderId, "cs_test_build");
        assertThat(page.get("checkoutUrl").asText()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_build");
        assertThat(page.get("orderNumber").asText()).isEqualTo(number);

        ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
        verify(stripe, times(1)).createCheckoutSession(params.capture(), eq("checkout-" + number + "-1"));
        SessionCreateParams sent = params.getValue();
        assertThat(sent.getMode()).isEqualTo(SessionCreateParams.Mode.PAYMENT);
        assertThat(sent.getPaymentMethodTypes()).isNull(); // payment methods are managed in the Dashboard
        assertThat(sent.getIntegrationIdentifier()).startsWith("ironoak-");
        assertThat(sent.getClientReferenceId()).isEqualTo(number);
        assertThat(sent.getCustomerEmail()).isEqualTo(EMAIL);
        assertThat(sent.getMetadata()).containsEntry("order_id", String.valueOf(orderId)).containsEntry("order_number", number);
        assertThat(sent.getSuccessUrl()).isEqualTo("https://shop.example.test/checkout/success?order=" + number
                + "&session_id={CHECKOUT_SESSION_ID}");
        assertThat(sent.getCancelUrl()).isEqualTo("https://shop.example.test/checkout/cancel?order=" + number);
        long minutes = (sent.getExpiresAt() - Instant.now().getEpochSecond()) / 60;
        assertThat(minutes).isBetween(30L, 31L);

        // the lines are the order's snapshot: the item, shipping, then taxes; they add up to grand_total
        assertThat(sent.getLineItems()).hasSize(3);
        var item = sent.getLineItems().get(0);
        assertThat(item.getPriceData().getUnitAmount()).isEqualTo(28999L);
        assertThat(item.getPriceData().getCurrency()).isEqualTo("usd");
        assertThat(item.getPriceData().getProductData().getName()).isEqualTo("AIR COMPRESSORS");
        assertThat(item.getQuantity()).isEqualTo(1L);
        assertThat(sent.getLineItems().get(1).getPriceData().getProductData().getName()).isEqualTo("Shipping");
        assertThat(sent.getLineItems().get(1).getPriceData().getUnitAmount()).isEqualTo(253L);
        assertThat(sent.getLineItems().get(2).getPriceData().getProductData().getName()).isEqualTo("Taxes");
        assertThat(sent.getLineItems().get(2).getPriceData().getUnitAmount()).isEqualTo(5510L);

        // one PENDING payment for the whole order; the stock hold now outlives the page
        assertThat(paymentStatus("cs_test_build")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select amount from payment where checkout_session_id = 'cs_test_build'",
                java.math.BigDecimal.class)).isEqualByComparingTo("347.62");
        OffsetDateTime hold = jdbc.queryForObject("select reservation_expires_at from customer_order where id = ?",
                OffsetDateTime.class, orderId);
        assertThat(hold.toEpochSecond()).isGreaterThan(sent.getExpiresAt());

        // asking again while the page is open returns the same page, without a new session
        doReturn(session("cs_test_build", "open")).when(stripe).retrieveSession("cs_test_build");
        JsonNode again = send(json(post("/api/orders/" + orderId + "/checkout-session"), "{\"email\":\"" + EMAIL + "\"}"), 200);
        assertThat(again.get("checkoutUrl").asText()).isEqualTo(page.get("checkoutUrl").asText());
        verify(stripe, times(1)).createCheckoutSession(any(), anyString());
        assertThat(jdbc.queryForObject("select count(*) from payment where order_id = ?", Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void anExpiredPageIsRetiredAndANewOneCreated() throws Exception {
        JsonNode order = placeOrder();
        long orderId = order.get("id").asLong();
        startCheckout(orderId, "cs_test_old");

        doReturn(session("cs_test_old", "expired")).when(stripe).retrieveSession("cs_test_old");
        doReturn(session("cs_test_new", "open")).when(stripe).createCheckoutSession(any(), anyString());
        JsonNode page = send(json(post("/api/orders/" + orderId + "/checkout-session"), "{\"email\":\"" + EMAIL + "\"}"), 200);

        assertThat(page.get("checkoutUrl").asText()).endsWith("cs_test_new");
        assertThat(paymentStatus("cs_test_old")).isEqualTo("EXPIRED");
        assertThat(paymentStatus("cs_test_new")).isEqualTo("PENDING");
        verify(stripe).createCheckoutSession(any(), eq("checkout-" + order.get("orderNumber").asText() + "-2"));
    }

    @Test
    void checkoutIsRefusedToStrangersAndForOrdersThatCannotBePaid() throws Exception {
        JsonNode order = placeOrder();
        long orderId = order.get("id").asLong();

        send(json(post("/api/orders/" + orderId + "/checkout-session"), "{\"email\":\"stranger@example.com\"}"), 404);
        send(json(post("/api/orders/999999/checkout-session"), "{\"email\":\"" + EMAIL + "\"}"), 404);
        send(json(post("/api/orders/" + orderId + "/checkout-session"), "{}"), 400);

        // Stripe not configured (blank key, nothing stubbed): 503 and no payment row
        send(json(post("/api/orders/" + orderId + "/checkout-session"), "{\"email\":\"" + EMAIL + "\"}"), 503);
        assertThat(jdbc.queryForObject("select count(*) from payment where order_id = ?", Integer.class, orderId)).isZero();

        // a cancelled order cannot be paid
        send(json(asAdmin(patch("/api/admin/orders/" + orderId + "/status")), "{\"status\":\"CANCELLED\"}"), 200);
        send(json(post("/api/orders/" + orderId + "/checkout-session"), "{\"email\":\"" + EMAIL + "\"}"), 422);

        // an order whose stock hold has run out cannot be paid either
        long late = placeOrder().get("id").asLong();
        jdbc.update("update customer_order set reservation_expires_at = now() - interval '1 minute' where id = ?", late);
        send(json(post("/api/orders/" + late + "/checkout-session"), "{\"email\":\"" + EMAIL + "\"}"), 422);
        // only the unconfigured attempt reached the gateway; the refused orders never did
        verify(stripe, times(1)).createCheckoutSession(any(), anyString());
    }

    // ---- webhook ---------------------------------------------------------------------------

    @Test
    void aPaidSessionConfirmsTheOrderExactlyOnce() throws Exception {
        JsonNode order = placeOrder();
        long orderId = order.get("id").asLong();
        startCheckout(orderId, "cs_test_paid");
        String event = sessionEvent("checkout.session.completed", order, "cs_test_paid", 34762, "paid", "pi_test_paid");

        // forged, unsigned or stale deliveries change nothing
        assertThat(mockMvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(event)).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mockMvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(event).header("Stripe-Signature", signature(event, "whsec_wrong", Instant.now().getEpochSecond())))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mockMvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(event).header("Stripe-Signature", signature(event, WEBHOOK_SECRET, Instant.now().getEpochSecond() - 3600)))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");

        assertThat(webhook(event)).isEqualTo(200);
        assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
        assertThat(paymentStatus("cs_test_paid")).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select payment_intent_id from payment where checkout_session_id = 'cs_test_paid'",
                String.class)).isEqualTo("pi_test_paid");
        assertThat(jdbc.queryForObject("select reservation_expires_at from customer_order where id = ?",
                OffsetDateTime.class, orderId)).isNull();

        // Stripe redelivers: recognised, nothing changes
        assertThat(webhook(event)).isEqualTo(200);
        String eventId = objectMapper.readTree(event).get("id").asText();
        assertThat(jdbc.queryForObject("select count(*) from payment_webhook_event where provider_event_id = ?",
                Integer.class, eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select processing_status || ':' || attempts from payment_webhook_event "
                + "where provider_event_id = ?", String.class, eventId)).isEqualTo("PROCESSED:1");
        assertThat(jdbc.queryForObject("select order_id from payment_webhook_event where provider_event_id = ?",
                Long.class, eventId)).isEqualTo(orderId);
        assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
    }

    @Test
    void aSessionChargedForTheWrongAmountIsNotTrustedAndStripeRetries() throws Exception {
        JsonNode order = placeOrder();
        long orderId = order.get("id").asLong();
        startCheckout(orderId, "cs_test_amount");

        String event = sessionEvent("checkout.session.completed", order, "cs_test_amount", 100, "paid", "pi_test_amount");
        String eventId = objectMapper.readTree(event).get("id").asText();
        assertThat(webhook(event)).isEqualTo(500);
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus("cs_test_amount")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select processing_status from payment_webhook_event where provider_event_id = ?",
                String.class, eventId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_error from payment_webhook_event where provider_event_id = ?",
                String.class, eventId)).contains("charged 100");
    }

    @Test
    void delayedPaymentMethodsSettleLaterAndFailuresCanBeRetried() throws Exception {
        JsonNode slow = placeOrder();
        startCheckout(slow.get("id").asLong(), "cs_test_slow");
        assertThat(webhook(sessionEvent("checkout.session.completed", slow, "cs_test_slow", 34762, "unpaid", "pi_test_slow"))).isEqualTo(200);
        assertThat(paymentStatus("cs_test_slow")).isEqualTo("PROCESSING");
        assertThat(orderStatus(slow.get("id").asLong())).isEqualTo("PENDING_PAYMENT");
        assertThat(webhook(sessionEvent("checkout.session.async_payment_succeeded", slow, "cs_test_slow", 34762, "paid", "pi_test_slow"))).isEqualTo(200);
        assertThat(orderStatus(slow.get("id").asLong())).isEqualTo("CONFIRMED");

        JsonNode failing = placeOrder();
        long failingId = failing.get("id").asLong();
        startCheckout(failingId, "cs_test_fail");
        webhook(sessionEvent("checkout.session.completed", failing, "cs_test_fail", 34762, "unpaid", "pi_test_fail"));
        assertThat(webhook(sessionEvent("checkout.session.async_payment_failed", failing, "cs_test_fail", 34762, "unpaid", "pi_test_fail"))).isEqualTo(200);
        assertThat(paymentStatus("cs_test_fail")).isEqualTo("FAILED");
        assertThat(orderStatus(failingId)).isEqualTo("PAYMENT_FAILED");
        assertThat(jdbc.queryForObject("select failure_code from payment where checkout_session_id = 'cs_test_fail'", String.class))
                .isEqualTo("async_payment_failed");

        // the customer tries again: a new page, and the order waits for payment again
        startCheckout(failingId, "cs_test_retry");
        assertThat(orderStatus(failingId)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus("cs_test_retry")).isEqualTo("PENDING");

        // an abandoned page that Stripe expires
        JsonNode abandoned = placeOrder();
        startCheckout(abandoned.get("id").asLong(), "cs_test_abandoned");
        assertThat(webhook(sessionEvent("checkout.session.expired", abandoned, "cs_test_abandoned", 34762, "unpaid", "pi_test_abandoned"))).isEqualTo(200);
        assertThat(paymentStatus("cs_test_abandoned")).isEqualTo("EXPIRED");
        assertThat(orderStatus(abandoned.get("id").asLong())).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void sessionsOfOtherIntegrationsAreIgnored() throws Exception {
        String foreign = """
                {"id":"evt_foreign","object":"event","api_version":"%s","created":%d,"type":"checkout.session.completed",
                 "data":{"object":{"id":"cs_test_foreign","object":"checkout.session","status":"complete",
                   "payment_status":"paid","amount_total":500,"currency":"usd","metadata":{}}}}
                """.formatted(Stripe.API_VERSION, Instant.now().getEpochSecond());
        assertThat(webhook(foreign)).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from payment where checkout_session_id = 'cs_test_foreign'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select processing_status from payment_webhook_event where provider_event_id = 'evt_foreign'",
                String.class)).isEqualTo("PROCESSED");
    }

    // ---- refunds ---------------------------------------------------------------------------

    @Test
    void staffRefundReturnsTheMoneyOnceAndOnlyForPaidStripePayments() throws Exception {
        JsonNode order = placeOrder();
        long orderId = order.get("id").asLong();
        startCheckout(orderId, "cs_test_refund");
        webhook(sessionEvent("checkout.session.completed", order, "cs_test_refund", 34762, "paid", "pi_test_refund"));
        long paymentId = paymentId("cs_test_refund");

        send(json(post("/api/admin/payments/" + paymentId + "/refund"), "{\"reason\":\"x\"}"), 401);
        send(json(asAdmin(patch("/api/admin/payments/" + paymentId + "/status")), "{\"status\":\"REFUNDED\"}"), 422);

        doReturn(refund("succeeded")).when(stripe).createRefund(any(), anyString());
        JsonNode refunded = send(json(asAdmin(post("/api/admin/payments/" + paymentId + "/refund")),
                "{\"reason\":\"Customer changed their mind\"}"), 200);
        assertThat(refunded.get("status").asText()).isEqualTo("REFUNDED");

        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        verify(stripe).createRefund(params.capture(), eq("refund-payment-" + paymentId));
        assertThat(params.getValue().getPaymentIntent()).isEqualTo("pi_test_refund");
        assertThat(((java.util.Map<?, ?>) params.getValue().getMetadata()).get("approved_by")).isEqualTo("owner");

        // asking again does not refund twice
        send(json(asAdmin(post("/api/admin/payments/" + paymentId + "/refund")), "{}"), 200);
        verify(stripe, times(1)).createRefund(any(), anyString());
        // refunding is not cancelling: the order is left for staff to decide
        assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");

        // a payment that was never paid cannot be refunded
        JsonNode unpaid = placeOrder();
        startCheckout(unpaid.get("id").asLong(), "cs_test_unpaid");
        send(json(asAdmin(post("/api/admin/payments/" + paymentId("cs_test_unpaid") + "/refund")), "{}"), 422);
    }

    @Test
    void aRefundStripeDeclinesLeavesThePaymentPaid() throws Exception {
        JsonNode order = placeOrder();
        startCheckout(order.get("id").asLong(), "cs_test_declined");
        webhook(sessionEvent("checkout.session.completed", order, "cs_test_declined", 34762, "paid", "pi_test_declined"));
        long paymentId = paymentId("cs_test_declined");

        doReturn(refund("failed")).when(stripe).createRefund(any(), anyString());
        send(json(asAdmin(post("/api/admin/payments/" + paymentId + "/refund")), "{}"), 502);
        assertThat(paymentStatus("cs_test_declined")).isEqualTo("PAID");
    }

    @Test
    void moneyArrivingForACancelledOrderIsRefundedAutomatically() throws Exception {
        JsonNode order = placeOrder();
        long orderId = order.get("id").asLong();
        startCheckout(orderId, "cs_test_late");
        send(json(asAdmin(patch("/api/admin/orders/" + orderId + "/status")), "{\"status\":\"CANCELLED\"}"), 200);

        doReturn(refund("succeeded")).when(stripe).createRefund(any(), anyString());
        assertThat(webhook(sessionEvent("checkout.session.completed", order, "cs_test_late", 34762, "paid", "pi_test_late"))).isEqualTo(200);

        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(paymentStatus("cs_test_late")).isEqualTo("REFUNDED");
        verify(stripe).createRefund(any(), eq("refund-payment-" + paymentId("cs_test_late")));
    }

    @Test
    void aRefundMadeInTheStripeDashboardIsRecorded() throws Exception {
        JsonNode order = placeOrder();
        startCheckout(order.get("id").asLong(), "cs_test_dashboard");
        webhook(sessionEvent("checkout.session.completed", order, "cs_test_dashboard", 34762, "paid", "pi_test_dashboard"));

        assertThat(webhook(chargeRefundedEvent("pi_test_dashboard", false))).isEqualTo(200);
        assertThat(paymentStatus("cs_test_dashboard")).isEqualTo("PAID"); // partial refunds are not modelled

        assertThat(webhook(chargeRefundedEvent("pi_test_dashboard", true))).isEqualTo(200);
        assertThat(paymentStatus("cs_test_dashboard")).isEqualTo("REFUNDED");
    }

    // ---- the cart --------------------------------------------------------------------------

    @Test
    void theCartCanQuoteShippingBeforeCheckout() throws Exception {
        long productId = products.findBySku("IO-AICO-001").orElseThrow().getId();
        String line = "\"items\":[{\"itemType\":\"PRODUCT\",\"referenceId\":" + productId + ",\"quantity\":1}]";

        JsonNode home = send(json(post("/api/shipping/quote"), "{\"country\":\"CO\",\"city\":\"Bogota\"," + line + "}"), 200);
        assertThat(home.get("domestic").asBoolean()).isTrue();
        assertThat(home.get("total").decimalValue()).isEqualByComparingTo("2.53");

        JsonNode abroad = send(json(post("/api/shipping/quote"), "{\"country\":\"ar\"," + line + "}"), 200);
        assertThat(abroad.get("domestic").asBoolean()).isFalse();
        assertThat(abroad.get("total").decimalValue()).isGreaterThan(home.get("total").decimalValue());

        send(json(post("/api/shipping/quote"), "{\"country\":\"FR\"," + line + "}"), 400);
        send(json(post("/api/shipping/quote"), "{" + line + "}"), 400);
        send(json(post("/api/shipping/quote"), "{\"country\":\"CO\",\"items\":[]}"), 400);
    }

    // ---- shipping rules and paying by order number ------------------------------------------

    @Test
    void machinesWaitForAStaffQuoteThenThePublicOrderNumberIsEnoughToPay() throws Exception {
        long machineId = jdbc.queryForObject("select id from milling_machine where model_code = 'BM-200'", Long.class);
        JsonNode order = send(json(post("/api/orders"),
                TestOrders.webJson("Ada Buyer", EMAIL, "MACHINE", machineId, 1)), 201);
        long orderId = order.get("id").asLong();
        String number = order.get("orderNumber").asText();
        assertThat(order.get("shippingStatus").asText()).isEqualTo("ON_REQUEST");
        assertThat(order.get("shippingCost").decimalValue()).isEqualByComparingTo("0");

        // cannot be paid until staff quote the freight
        send(json(post("/api/orders/" + orderId + "/checkout-session"), "{\"email\":\"" + EMAIL + "\"}"), 422);
        assertThat(send(get("/api/orders/" + number), 200).get("payable").asBoolean()).isFalse();

        send(json(patch("/api/admin/orders/" + orderId + "/shipping"), "{\"shippingCost\":350.00}"), 401);
        JsonNode quoted = send(json(asAdmin(patch("/api/admin/orders/" + orderId + "/shipping")),
                "{\"shippingCost\":350.00}"), 200);
        assertThat(quoted.get("shippingStatus").asText()).isEqualTo("QUOTED");
        assertThat(quoted.get("grandTotal").decimalValue())
                .isEqualByComparingTo(quoted.get("subtotal").decimalValue().add(new java.math.BigDecimal("350.00"))
                        .add(quoted.get("taxes").decimalValue()));

        // the order number alone shows the order, without personal data, and pays it
        JsonNode view = send(get("/api/orders/" + number.toLowerCase()), 200);
        assertThat(view.get("payable").asBoolean()).isTrue();
        assertThat(view.get("grandTotal").decimalValue()).isEqualByComparingTo(quoted.get("grandTotal").decimalValue());
        assertThat(view.toString()).doesNotContain(EMAIL).doesNotContain("Calle").doesNotContain("Ada Buyer");
        send(get("/api/orders/IO-20260101-NOSUCHNO"), 404);

        doReturn(session("cs_test_by_number", "open")).when(stripe).createCheckoutSession(any(), anyString());
        send(post("/api/orders/" + number + "/checkout-session"), 200);
        ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
        verify(stripe).createCheckoutSession(params.capture(), anyString());
        assertThat(params.getValue().getCustomerEmail()).isNull();
        assertThat(params.getValue().getLineItems().get(1).getPriceData().getUnitAmount()).isEqualTo(35000L);
    }

    @Test
    void colombianTownsOutsideTheListAreRefusedUntilStaffAddARoute() throws Exception {
        long productId = products.findBySku("IO-AICO-001").orElseThrow().getId();
        String body = "{\"country\":\"CO\",\"city\":\"Zipaquira\",\"items\":[{\"itemType\":\"PRODUCT\",\"referenceId\":"
                + productId + ",\"quantity\":1}]}";
        send(json(post("/api/shipping/quote"), body), 400);

        send(json(asAdmin(put("/api/admin/shipping/routes")),
                "{\"country\":\"CO\",\"city\":\"Zipaquirá\",\"distanceKm\":50}"), 200);
        JsonNode quote = send(json(post("/api/shipping/quote"), body), 200);
        assertThat(quote.get("source").asText()).isEqualTo("MANUAL");
        assertThat(quote.get("distanceKm").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(quote.get("estimated").asBoolean()).isFalse();
        assertThat(send(asAdmin(get("/api/admin/shipping/routes")), 200).toString()).contains("Zipaquirá");
        // air countries have no road route to set
        send(json(asAdmin(put("/api/admin/shipping/routes")),
                "{\"country\":\"US\",\"city\":\"Miami\",\"distanceKm\":2400}"), 422);
    }

    @Test
    void airCountriesPayTheirFixedAirPrice() throws Exception {
        long productId = products.findBySku("IO-AICO-001").orElseThrow().getId();
        JsonNode quote = send(json(post("/api/shipping/quote"), "{\"country\":\"US\",\"city\":\"Miami\","
                + "\"items\":[{\"itemType\":\"PRODUCT\",\"referenceId\":" + productId + ",\"quantity\":1}]}"), 200);
        assertThat(quote.get("mode").asText()).isEqualTo("AIR");
        assertThat(quote.get("status").asText()).isEqualTo("QUOTED");
        assertThat(quote.get("source").asText()).isEqualTo("FIXED");
        assertThat(quote.get("total").decimalValue()).isEqualByComparingTo("86");
    }
}
