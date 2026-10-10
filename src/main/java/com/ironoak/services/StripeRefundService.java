package com.ironoak.services;

import com.ironoak.domain.Payment;
import com.ironoak.domain.enums.PaymentStatus;
import com.ironoak.dto.response.PaymentResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.PaymentProviderException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.PaymentMapper;
import com.ironoak.repository.PaymentRepository;
import com.ironoak.security.AuditLog;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

/**
 * Returns a Stripe payment in full. Refunds are approved by staff in the admin
 * panel
 * (POST /api/admin/payments/{id}/refund); the only automatic refund is for
 * money that arrives
 * after its order was already cancelled or expired. Refunding does not cancel
 * the order: staff
 * do that separately. Partial refunds are not modelled (payment_status has no
 * such value).
 */
@Service
public class StripeRefundService {

    private static final Set<String> ACCEPTED_REFUND_STATES = Set.of("succeeded", "pending");

    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final PaymentMapper mapper;
    private final StripeGateway stripe;
    private final TransactionTemplate transactions;

    public StripeRefundService(PaymentRepository payments,
            PaymentService paymentService,
            PaymentMapper mapper,
            StripeGateway stripe,
            PlatformTransactionManager transactionManager) {

        this.payments = payments;
        this.paymentService = paymentService;
        this.mapper = mapper;
        this.stripe = stripe;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Idempotent: refunding an already refunded payment just returns it. */
    public PaymentResponse refund(Long paymentId,
            String reason,
            String actor,
            String ip) {

        record Target(String paymentIntentId,
                String orderNumber,
                boolean alreadyRefunded) {
        }
        Target target = transactions.execute(status -> {

            Payment payment = payments.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

            if (!StripeCheckoutService.PROVIDER.equals(payment.getProvider())) {

                throw new BusinessRuleException("Only Stripe payments can be refunded here");
            }
            if (payment.getStatus() == PaymentStatus.REFUNDED) {

                return new Target(null, null, true);
            }
            if (payment.getStatus() != PaymentStatus.PAID) {

                throw new BusinessRuleException("Payment " + paymentId + " is " + payment.getStatus()
                        + "; only a PAID payment can be refunded");
            }
            if (payment.getPaymentIntentId() == null) {
                throw new BusinessRuleException("Payment " + paymentId + " has no Stripe payment intent yet");
            }
            return new Target(payment.getPaymentIntentId(), payment.getOrder().getOrderNumber(), false);
        });
        if (target.alreadyRefunded()) {

            return transactions.execute(status -> mapper.toResponse(payments.findById(paymentId).orElseThrow()));
        }

        Refund refund = stripe.createRefund(RefundCreateParams.builder()
                .setPaymentIntent(target.paymentIntentId())
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .putMetadata("payment_id", String.valueOf(paymentId))
                .putMetadata("order_number", target.orderNumber())
                .putMetadata("approved_by", actor == null ? "system" : actor)
                .putMetadata("note", reason == null ? "" : reason)
                .build(), "refund-payment-" + paymentId);

        if (refund.getStatus() == null || !ACCEPTED_REFUND_STATES.contains(refund.getStatus())) {

            AuditLog.warn("refund not accepted by Stripe (" + refund.getStatus() + ") payment=" + paymentId, actor, ip);
            throw PaymentProviderException.failed("Stripe did not accept the refund: " + refund.getStatus(), null);
        }

        PaymentResponse refunded = transactions.execute(status -> {

            Payment payment = payments.findById(paymentId).orElseThrow();
            paymentService.markRefunded(payment);

            return mapper.toResponse(payment);
        });

        AuditLog.warn("refund issued payment=" + paymentId + " order=" + target.orderNumber()
                + " reason=" + (reason == null ? "-" : reason), actor, ip);

        return refunded;
    }
}
