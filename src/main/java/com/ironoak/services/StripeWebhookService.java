package com.ironoak.services;

import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.Payment;
import com.ironoak.domain.PaymentWebhookEvent;
import com.ironoak.domain.enums.PaymentStatus;
import com.ironoak.domain.enums.WebhookProcessingStatus;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.PaymentRepository;
import com.ironoak.repository.PaymentWebhookEventRepository;
import com.ironoak.security.AuditLog;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Applies Stripe's events to payments and orders.
 *
 * Every verified event is first written to payment_webhook_event (unique by
 * Stripe's event id),
 * so a redelivery is recognised and a failure can be retried: when processing
 * throws, the event is
 * marked FAILED and the endpoint answers 500, which makes Stripe resend it. All
 * handlers are
 * idempotent. Fulfilment (confirming the order) happens here only, never on the
 * return page.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private static final Set<PaymentStatus> OPEN = Set.of(PaymentStatus.PENDING,
            PaymentStatus.PROCESSING);

    private final StripeGateway stripe;
    private final PaymentWebhookEventRepository events;
    private final PaymentRepository payments;
    private final CustomerOrderRepository orders;
    private final PaymentService paymentService;
    private final StripeRefundService refunds;
    private final TransactionTemplate transactions;

    public StripeWebhookService(StripeGateway stripe,
            PaymentWebhookEventRepository events,
            PaymentRepository payments,
            CustomerOrderRepository orders,
            PaymentService paymentService,
            StripeRefundService refunds,
            PlatformTransactionManager transactionManager) {
        this.stripe = stripe;
        this.events = events;
        this.payments = payments;
        this.orders = orders;
        this.paymentService = paymentService;
        this.refunds = refunds;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Verifies the signature (throws if invalid), then processes the event at most
     * once successfully.
     */
    public void handle(byte[] payload,
            String signatureHeader) {

        Event event = stripe.verifyWebhook(payload, signatureHeader);

        Long ledgerId;
        try {

            ledgerId = transactions.execute(status -> begin(event));

        } catch (DataIntegrityViolationException | UnexpectedRollbackException raced) {

            // the same event arrived twice at once; the other delivery handles it
            log.debug("Stripe event {} is being handled by another delivery", event.getId());
            return;
        }
        if (ledgerId == null) {
            log.debug("Stripe event {} already processed", event.getId());
            return;
        }
        try {
            Long refundPaymentId = transactions.execute(status -> apply(event, ledgerId));
            if (refundPaymentId != null) {
                // money arrived for an order that was already closed: give it back
                refunds.refund(refundPaymentId, "Order was closed before the payment arrived", "system", null);
            }
            transactions.executeWithoutResult(status -> events.findById(ledgerId)
                    .ifPresent(PaymentWebhookEvent::markProcessed));

        } catch (RuntimeException e) {

            log.error("Stripe event {} ({}) failed", event.getId(), event.getType(), e);
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            transactions.executeWithoutResult(status -> events.findById(ledgerId)
                    .ifPresent(row -> row.markFailed(message.length() > 1000 ? message.substring(0, 1000) : message)));
            throw e;
        }
    }

    /**
     * Returns the ledger row to process, or null when this event was already
     * processed.
     */
    private Long begin(Event event) {

        Optional<PaymentWebhookEvent> existing = events.findByProviderEventId(event.getId());

        if (existing.isPresent()) {

            if (existing.get().getProcessingStatus() == WebhookProcessingStatus.PROCESSED) {
                return null;
            }
            existing.get().markProcessing();
            return existing.get().getId();
        }
        PaymentWebhookEvent row = new PaymentWebhookEvent(event.getId(),
                StripeCheckoutService.PROVIDER,
                event.getType(), objectId(event));
        row.markProcessing();

        return events.saveAndFlush(row).getId();
    }

    /**
     * Runs the handler for the event type. Returns a payment to refund after
     * commit, if any.
     */
    private Long apply(Event event, Long ledgerId) {

        StripeObject object = event.getDataObjectDeserializer().getObject().orElseGet(() -> {
            try {
                return event.getDataObjectDeserializer().deserializeUnsafe();

            } catch (Exception e) {
                throw new IllegalStateException("Cannot read the payload of event " + event.getId(), e);
            }
        });
        Payment touched = null;
        Long refund = null;

        switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {

                Session session = (Session) object;

                touched = findOrCreatePayment(session);

                if (touched != null) {

                    refund = onSessionCompleted(touched, session);
                }
            }
            case "checkout.session.async_payment_failed" -> {

                touched = findOrCreatePayment((Session) object);

                if (touched != null) {

                    close(touched, PaymentStatus.FAILED, "async_payment_failed",
                            "The delayed payment method was not completed");
                }
            }
            case "checkout.session.expired" -> {
                touched = payments.findByCheckoutSessionId(((Session) object).getId()).orElse(null);

                if (touched != null) {
                    close(touched, PaymentStatus.EXPIRED, null, null);
                }
            }
            case "charge.refunded" -> touched = onChargeRefunded((Charge) object);
            default -> log.debug("Ignoring Stripe event type {}", event.getType());

        }
        if (touched != null) {

            Payment linked = touched;
            events.findById(ledgerId).ifPresent(row -> {
                row.setPayment(linked);
                row.setOrder(linked.getOrder());
            });
        }
        return refund;
    }

    private Long onSessionCompleted(Payment payment, Session session) {

        long expected = payment.getAmount().movePointRight(2).longValueExact();

        if (session.getAmountTotal() == null || session.getAmountTotal() != expected
                || !payment.getCurrency().equalsIgnoreCase(session.getCurrency())) {

            throw new IllegalStateException("Stripe session " + session.getId() + " charged "
                    + session.getAmountTotal() + " " + session.getCurrency() + " but payment "
                    + payment.getId() + " expects " + expected + " " + payment.getCurrency());
        }
        if ("paid".equals(session.getPaymentStatus())) {

            PaymentService.SettleOutcome outcome = paymentService.settleFromProvider(payment,
                    session.getPaymentIntent());

            if (outcome == PaymentService.SettleOutcome.REFUND_NEEDED) {

                AuditLog.warn("payment " + payment.getId() + " arrived for closed order "
                        + payment.getOrder().getOrderNumber(), "stripe", null);

                return payment.getId();
            }
            return null;
        }
        // A delayed method (for example a bank debit): the money has not arrived yet
        if (payment.getPaymentIntentId() == null && session.getPaymentIntent() != null) {

            payment.setPaymentIntentId(session.getPaymentIntent());
        }
        if (payment.getStatus() == PaymentStatus.PENDING) {

            paymentService.updateStatus(payment.getId(), PaymentStatus.PROCESSING);
        }
        return null;
    }

    private void close(Payment payment,
            PaymentStatus target,
            String code,
            String message) {

        if (OPEN.contains(payment.getStatus())) {

            paymentService.updateStatus(payment.getId(), target, code, message);
        }
    }

    private Payment onChargeRefunded(Charge charge) {

        if (charge.getPaymentIntent() == null) {
            return null;
        }
        Payment payment = payments.findByPaymentIntentId(charge.getPaymentIntent()).orElse(null);

        if (payment == null) {
            return null;
        }
        if (Boolean.TRUE.equals(charge.getRefunded())) {

            paymentService.markRefunded(payment);

        } else {
            log.info("Partial refund of {} on payment {} is not modelled; left as is", charge.getAmountRefunded(),
                    payment.getId());
        }
        return payment;
    }

    /**
     * The payment for a session. If we never saved it (the app failed between
     * creating the session
     * and recording it), it is rebuilt from the session's own metadata. Sessions
     * that do not carry
     * our order metadata belong to some other integration and are ignored.
     */
    private Payment findOrCreatePayment(Session session) {

        Optional<Payment> existing = payments.findByCheckoutSessionId(session.getId());

        if (existing.isPresent()) {

            return existing.get();
        }
        String orderId = session.getMetadata() == null ? null : session.getMetadata().get("order_id");
        String orderNumber = session.getMetadata() == null ? null : session.getMetadata().get("order_number");

        if (orderId == null || orderNumber == null) {

            log.warn("Stripe session {} has no order metadata; ignored", session.getId());
            return null;
        }
        CustomerOrder order = orders.findById(Long.parseLong(orderId))
                .filter(o -> o.getOrderNumber().equals(orderNumber))
                .orElseThrow(() -> new IllegalStateException("Stripe session " + session.getId()
                        + " names unknown order " + orderNumber));

        // The amount is the order's own total, never Stripe's figure:
        // onSessionCompleted then
        // compares the two, so a session charged for a different amount is caught.
        Payment payment = new Payment(order, order.getGrandTotal(),
                StripeCheckoutService.PROVIDER, session.getId());

        payment.setCheckoutSessionId(session.getId());

        payment.setCurrency(session.getCurrency() == null ? order.getCurrency()
                : session.getCurrency().toUpperCase(Locale.ROOT));

        log.warn("Rebuilt payment for Stripe session {} (order {})", session.getId(), orderNumber);

        return payments.saveAndFlush(payment);
    }

    private static String objectId(Event event) {

        return event.getDataObjectDeserializer().getObject().map(o -> {

            try {
                return (String) o.getClass().getMethod("getId").invoke(o);

            } catch (ReflectiveOperationException e) {
                return null;
            }
        }).orElse(null);
    }
}
