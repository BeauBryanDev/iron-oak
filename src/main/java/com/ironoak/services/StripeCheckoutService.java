package com.ironoak.services;

import com.ironoak.domain.enums.ShippingStatus;
import com.ironoak.config.CheckoutProperties;
import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.OrderItem;
import com.ironoak.domain.Payment;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.domain.enums.PaymentStatus;
import com.ironoak.dto.response.CheckoutSessionResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.PaymentProviderException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.PaymentRepository;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Starts a Stripe-hosted Checkout page for an unpaid order.
 *
 * Amounts always come from the order's own snapshots (item prices, shipping,
 * taxes), never from
 * the client, and must add up to the order's grand total. The database work and
 * the Stripe call
 * are kept in separate steps so no database transaction stays open during the
 * network call.
 * Asking again for an order whose page is still open returns the same page.
 */
@Service
public class StripeCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutService.class);

    /**
     * Tags sessions of this integration in the Stripe Dashboard (label plus 8
     * random letters).
     */
    static final String INTEGRATION_IDENTIFIER = "ironoak-web-checkout-kqzvmtrx";
    static final String PROVIDER = "stripe";
    private static final Set<OrderStatus> PAYABLE = Set.of(OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED);

    private record Line(String name, String code, long unitCents, long quantity) {
    }

    /** Everything needed to call Stripe, read inside one short transaction. */
    private record Plan(Long orderId,
            String orderNumber,
            String currency,
            String customerEmail,
            List<Line> lines,
            long totalCents,
            OffsetDateTime sessionExpiresAt,
            int attempt,
            String existingSessionId,
            Long existingPaymentId) {
    }

    private final CustomerOrderRepository orders;
    private final PaymentRepository payments;
    private final OrderService orderService;
    private final StripeGateway stripe;
    private final CheckoutProperties checkout;
    private final TransactionTemplate transactions;

    public StripeCheckoutService(CustomerOrderRepository orders,
            PaymentRepository payments,
            OrderService orderService,
            StripeGateway stripe,
            CheckoutProperties checkout,
            PlatformTransactionManager transactionManager) {

        this.orders = orders;
        this.payments = payments;
        this.orderService = orderService;
        this.stripe = stripe;
        this.checkout = checkout;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public CheckoutSessionResponse createSession(Long orderId, String email) {
        return createSession(orderId, email, true);
    }

    /**
     * Pays an order by its order number alone (the cart's "pay my order" field, for
     * orders Piper
     * created). Holding the number is enough to pay; the customer's email is not
     * put on the page.
     */
    public CheckoutSessionResponse createSessionByNumber(String orderNumber) {

        String number = orderNumber == null ? "" : orderNumber.trim().toUpperCase(java.util.Locale.ROOT);
        Long orderId = orders.findIdByOrderNumber(number)
                .orElseThrow(() -> new ResourceNotFoundException("Order", number));

        return createSession(orderId, null, false);
    }

    private CheckoutSessionResponse createSession(Long orderId, String email,
            boolean checkOwner) {

        for (int round = 0; round < 2; round++) {

            Plan plan = transactions.execute(status -> prepare(orderId, email, checkOwner));

            if (plan.existingSessionId() != null) {

                Session existing = stripe.retrieveSession(plan.existingSessionId());

                if ("open".equals(existing.getStatus()) && existing.getUrl() != null) {

                    return response(plan.orderNumber(), plan.existingPaymentId(), existing);
                }
                if ("complete".equals(existing.getStatus())) {

                    throw new BusinessRuleException("A payment for order " + plan.orderNumber()
                            + " was already submitted; it will be confirmed shortly");
                }
                // the page expired: retire that attempt and plan a fresh one
                transactions.executeWithoutResult(status -> payments.findById(plan.existingPaymentId())
                        .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                        .ifPresent(p -> {
                            p.markExpired();
                            payments.save(p);
                        }));
                continue;
            }
            String idempotencyKey = "checkout-" + plan.orderNumber() + "-" + plan.attempt();
            Session session = stripe.createCheckoutSession(params(plan), idempotencyKey);
            Payment saved = transactions.execute(status -> recordPayment(plan, session, idempotencyKey));

            return response(plan.orderNumber(), saved.getId(), session);
        }
        throw new BusinessRuleException("Could not start a payment for this order; try again");
    }

    private Plan prepare(Long orderId, String email, boolean checkOwner) {

        CustomerOrder order = orders.findLockedById(orderId)
                .filter(o -> !checkOwner || OrderService.ownedBy(o, email))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!PAYABLE.contains(order.getStatus())) {

            throw new BusinessRuleException("Order " + order.getOrderNumber() + " is " + order.getStatus()
                    + " and cannot be paid");
        }
        if (order.getShippingStatus() == ShippingStatus.ON_REQUEST) {
            throw new BusinessRuleException("Shipping for order " + order.getOrderNumber()
                    + " is being quoted by our staff; it can be paid once the quote is ready");
        }
        OffsetDateTime now = OffsetDateTime.now();

        if (order.getReservationExpiresAt() != null && !order.getReservationExpiresAt().isAfter(now)) {

            throw new BusinessRuleException("The reservation for order " + order.getOrderNumber()
                    + " has expired; place the order again");
        }
        if (payments.sumAmountByOrderAndStatus(order.getId(), PaymentStatus.PAID).signum() > 0) {

            throw new BusinessRuleException("Order " + order.getOrderNumber() + " already has a payment");
        }
        List<Payment> attempts = payments.findByOrderIdOrderByCreatedAtDesc(order.getId());

        Payment open = attempts.stream()
                .filter(p -> PROVIDER.equals(p.getProvider()) && p.getStatus() == PaymentStatus.PENDING
                        && p.getCheckoutSessionId() != null)
                .findFirst().orElse(null);

        int attempt = (int) attempts.stream().filter(p -> PROVIDER.equals(p.getProvider())).count() + 1;

        List<Line> lines = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            lines.add(new Line(item.getItemName(),
                    item.getItemCode(),
                    cents(item.getUnitPrice()),
                    item.getQuantity()));
        }
        if (order.getShippingCost().signum() > 0) {

            lines.add(new Line("Shipping", "SHIPPING",
                    cents(order.getShippingCost()), 1));
        }
        if (order.getTaxes().signum() > 0) {

            lines.add(new Line("Taxes", "TAXES",
                    cents(order.getTaxes()), 1));
        }
        long total = lines.stream().mapToLong(l -> l.unitCents() * l.quantity()).sum();

        if (total != cents(order.getGrandTotal())) {

            log.error("Order {} lines add up to {} cents but grand_total is {}",
                    order.getOrderNumber(), total,
                    order.getGrandTotal());

            throw new IllegalStateException("Order " + order.getOrderNumber() + " totals are inconsistent");
        }

        OffsetDateTime sessionExpires = now.plusMinutes(checkout.getSessionMinutes());
        if (open == null) {
            // The stock hold must outlive the payment page, or a late payment would find
            // the stock gone.
            OffsetDateTime holdUntil = sessionExpires.plusMinutes(1);

            // An order that already holds stock longer (Piper, staff quote) keeps that
            // hold.
            OffsetDateTime cap = order.getCreatedAt().plusMinutes(checkout.getMaxHoldMinutes());
            if (order.getReservationExpiresAt() != null && order.getReservationExpiresAt().isAfter(cap)) {
                cap = order.getReservationExpiresAt();
            }
            if (holdUntil.isAfter(cap)) {

                throw new BusinessRuleException("The reservation for order " + order.getOrderNumber()
                        + " can no longer be extended; place the order again");
            }
            orderService.reopenForPayment(order); // PAYMENT_FAILED -> PENDING_PAYMENT
            if (order.getReservationExpiresAt() == null || order.getReservationExpiresAt().isBefore(holdUntil)) {

                order.setReservationExpiresAt(holdUntil);
            }
            orders.save(order);
        }
        return new Plan(order.getId(), order.getOrderNumber(),
                order.getCurrency(),
                checkOwner ? order.getCustomerEmail() : null,
                lines,
                total,
                sessionExpires,
                attempt, open == null ? null : open.getCheckoutSessionId(),
                open == null ? null : open.getId());
    }

    private SessionCreateParams params(Plan plan) {

        String base = checkout.getFrontendBaseUrl().replaceAll("/+$", "");
        String reference = plan.orderNumber();
        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setIntegrationIdentifier(INTEGRATION_IDENTIFIER)
                .setClientReferenceId(reference)
                .setExpiresAt(plan.sessionExpiresAt().toEpochSecond())
                .setSuccessUrl(base + "/checkout/success?order=" + reference + "&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(base + "/checkout/cancel?order=" + reference)
                .putMetadata("order_id", String.valueOf(plan.orderId()))
                .putMetadata("order_number", reference)
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .setDescription("Iron & Oak order " + reference)
                        .putMetadata("order_id", String.valueOf(plan.orderId()))
                        .putMetadata("order_number", reference)
                        .build());

        if (plan.customerEmail() != null) {
            builder.setCustomerEmail(plan.customerEmail());
        }
        String currency = plan.currency().toLowerCase();

        for (Line line : plan.lines()) {

            builder.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity(line.quantity())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency)
                            .setUnitAmount(line.unitCents())
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(line.name())
                                    .putMetadata("code", line.code())
                                    .build())
                            .build())
                    .build());
        }
        return builder.build();
    }

    private Payment recordPayment(Plan plan,
            Session session,
            String idempotencyKey) {

        CustomerOrder order = orders.getReferenceById(plan.orderId());

        Payment payment = new Payment(order, BigDecimal.valueOf(plan.totalCents(), 2),
                PROVIDER, session.getId());

        payment.setCheckoutSessionId(session.getId());
        payment.setIdempotencyKey(idempotencyKey);

        if (session.getPaymentIntent() != null) {

            payment.setPaymentIntentId(session.getPaymentIntent());
        }
        return payments.saveAndFlush(payment);
    }

    private static CheckoutSessionResponse response(String orderNumber,
            Long paymentId,
            Session session) {

        OffsetDateTime expires = session.getExpiresAt() == null ? null
                : OffsetDateTime.ofInstant(Instant.ofEpochSecond(session.getExpiresAt()),
                        ZoneOffset.UTC);

        if (session.getUrl() == null) {

            throw PaymentProviderException.failed("Stripe returned no checkout URL", null);
        }
        return new CheckoutSessionResponse(orderNumber, paymentId,
                session.getUrl(), expires);
    }

    private static long cents(BigDecimal amount) {

        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact();
    }
}
