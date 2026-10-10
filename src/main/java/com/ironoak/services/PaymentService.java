package com.ironoak.services;

import com.ironoak.domain.enums.ShippingStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.ironoak.dto.request.PaymentFilter;
import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.Payment;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.domain.enums.PaymentStatus;
import com.ironoak.dto.request.CreatePaymentRequest;
import com.ironoak.dto.response.PaymentResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.PaymentMapper;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Payment attempts against an order, written by staff today and by the payment
 * provider's
 * callbacks later, never by Piper. The amount is always derived from the
 * order's unpaid balance,
 * never taken from the client. A payment that completes the order total
 * confirms the order;
 * REFUNDED is recorded on the payment only (no money is moved here).
 */
@Service
@Transactional
public class PaymentService {

    private static final Set<OrderStatus> PAYABLE = Set.of(OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED,
            OrderStatus.CONFIRMED,
            OrderStatus.IN_PROGRESS,
            OrderStatus.COMPLETED);

    /**
     * An attempt that is still open can end any way; a PAID one can only be
     * refunded; the rest are final.
     */
    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = Map.of(
            PaymentStatus.PENDING,
            Set.of(PaymentStatus.PROCESSING,
                    PaymentStatus.PAID,
                    PaymentStatus.FAILED,
                    PaymentStatus.EXPIRED,
                    PaymentStatus.CANCELLED),
            PaymentStatus.PROCESSING,
            Set.of(PaymentStatus.PAID,
                    PaymentStatus.FAILED,
                    PaymentStatus.EXPIRED,
                    PaymentStatus.CANCELLED),
            PaymentStatus.PAID,
            Set.of(PaymentStatus.REFUNDED),
            PaymentStatus.FAILED,
            Set.of(),
            PaymentStatus.EXPIRED,
            Set.of(),
            PaymentStatus.CANCELLED,
            Set.of(),
            PaymentStatus.REFUNDED,
            Set.of());

    private static final Set<PaymentStatus> OPEN = Set.of(PaymentStatus.PENDING,
            PaymentStatus.PROCESSING);

    private final PaymentRepository payments;
    private final CustomerOrderRepository orders;
    private final OrderService orderService;
    private final PaymentMapper mapper;
    private final EntityManager entityManager;

    public PaymentService(PaymentRepository payments,
            CustomerOrderRepository orders,
            OrderService orderService,
            PaymentMapper mapper,
            EntityManager entityManager) {
        this.payments = payments;
        this.orders = orders;
        this.orderService = orderService;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    /**
     * Opens a PENDING payment for what is still unpaid on the order. Repeating a
     * call
     * with the same provider and reference returns the existing payment, so a
     * retried
     * request or webhook does not create a second one.
     */
    public PaymentResponse create(CreatePaymentRequest request) {

        CustomerOrder order = orders.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));

        String provider = request.provider().trim();

        String reference = request.providerReference() == null || request.providerReference().isBlank()
                ? null
                : request.providerReference().trim();

        if (reference != null) {

            var existing = payments.findByProviderAndProviderReference(provider, reference);

            if (existing.isPresent()) {

                if (!existing.get().getOrder().getId().equals(order.getId())) {

                    throw new BusinessRuleException("That payment reference belongs to another order");
                }
                return mapper.toResponse(existing.get());
            }
        }
        if (!PAYABLE.contains(order.getStatus())) {

            throw new BusinessRuleException(

                    "Order " + order.getId() + " is " + order.getStatus() + " and cannot be paid");
        }
        if (order.getShippingStatus() == ShippingStatus.ON_REQUEST) {
            throw new BusinessRuleException("Shipping for order " + order.getOrderNumber()
                    + " has not been quoted yet; set it with PATCH /api/admin/orders/" + order.getId() + "/shipping");
        }
        BigDecimal due = order.getGrandTotal().subtract(paidSoFar(order));

        if (due.signum() <= 0) {

            throw new BusinessRuleException("Order " + order.getId() + " has nothing left to pay");
        }
        orderService.reopenForPayment(order); // PAYMENT_FAILED -> PENDING_PAYMENT; refuses an expired hold

        return mapper.toResponse(payments.saveAndFlush(new Payment(order, due,
                provider, reference)));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(PaymentFilter filter,
            Pageable pageable) {

        Specification<Payment> spec = Specification.allOf(
                FilterSpecs.in("status", filter.status()),
                FilterSpecs.hasText(filter.provider()) ? FilterSpecs.equal("provider",
                        filter.provider().trim()) : null,
                FilterSpecs.dateRange("createdAt",
                        filter.from(), filter.to()),
                matching(filter.q()));

        return payments.findAll(spec, pageable).map(mapper::toResponse);
    }

    private static Specification<Payment> matching(String search) {

        if (!FilterSpecs.hasText(search)) {
            return null;
        }
        return (root, query, cb) -> {

            Predicate reference = FilterSpecs.anyContains(cb, search, root.<String>get("providerReference"));
            String trimmed = search.trim();

            return trimmed.matches("\\d{1,18}")
                    ? cb.or(reference, cb.equal(root.get("order").get("id"),
                            Long.parseLong(trimmed)))
                    : reference;
        };
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {

        return mapper.toResponse(payments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id)));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForOrder(Long orderId) {

        return mapper.toResponses(payments.findByOrderIdOrderByCreatedAtDesc(orderId));
    }

    /**
     * Moves a payment along TRANSITIONS and keeps the order in step: a payment that
     * brings the
     * PAID total up to the order total confirms the order, and a failed last
     * attempt marks it
     * PAYMENT_FAILED. Total PAID never exceeds the order total.
     */
    public PaymentResponse updateStatus(Long id, PaymentStatus newStatus) {

        return updateStatus(id, newStatus, null, null);
    }

    /**
     * failureCode and failureMessage are recorded when the new status is FAILED
     * (for example a card decline).
     */
    public PaymentResponse updateStatus(Long id,
            PaymentStatus newStatus,
            String failureCode,
            String failureMessage) {

        Payment payment = payments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));

        if (!TRANSITIONS.get(payment.getStatus()).contains(newStatus)) {

            throw new BusinessRuleException(
                    "Cannot change payment " + id + " from " + payment.getStatus() + " to " + newStatus);
        }
        if (newStatus == PaymentStatus.REFUNDED && "stripe".equals(payment.getProvider())) {

            throw new BusinessRuleException("Stripe payments are refunded with POST /api/admin/payments/" + id
                    + "/refund, which moves the money");
        }
        CustomerOrder order = lock(payment.getOrder());

        switch (newStatus) {

            case PROCESSING -> payment.markProcessing();

            case PAID -> {
                BigDecimal paidAfter = paidSoFar(order).add(payment.getAmount());

                if (paidAfter.compareTo(order.getGrandTotal()) > 0) {

                    throw new BusinessRuleException("Paying this would exceed the total of order " + order.getId());
                }
                payment.markPaid();

                if (paidAfter.compareTo(order.getGrandTotal()) == 0) {
                    orderService.confirmAfterPayment(order);
                }
            }
            case FAILED -> {

                payment.markFailed(failureCode, failureMessage);

                if (payments.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                        .noneMatch(p -> !p.getId().equals(payment.getId())
                                && (OPEN.contains(p.getStatus()) || p.getStatus() == PaymentStatus.PAID))) {

                    orderService.markPaymentFailed(order);
                }
            }
            case EXPIRED -> payment.markExpired();
            case CANCELLED -> payment.markCancelled();
            case REFUNDED -> payment.markRefunded();
            default -> throw new BusinessRuleException("Unsupported payment status " + newStatus);
        }
        return mapper.toResponse(payments.saveAndFlush(payment));
    }

    /** What recording a provider-confirmed payment did to the order. */
    public enum SettleOutcome {
        /**
         * The payment is recorded and the order is paid (or still waiting for the
         * rest).
         */
        RECORDED,
        /**
         * The money arrived but the order was already expired or cancelled: it must be
         * refunded.
         */
        REFUND_NEEDED,
        /** Nothing to do: the payment was already refunded. */
        ALREADY_REFUNDED
    }

    /**
     * Records that the provider confirmed this payment, whatever state we had it in
     * (an expired
     * attempt can still be paid). Safe to call again for the same event: it only
     * repeats the
     * refund-needed answer while the money has not been returned.
     */
    public SettleOutcome settleFromProvider(Payment payment, String paymentIntentId) {

        if (payment.getStatus() == PaymentStatus.REFUNDED) {

            return SettleOutcome.ALREADY_REFUNDED;
        }
        if (payment.getPaymentIntentId() == null && paymentIntentId != null) {

            payment.setPaymentIntentId(paymentIntentId);
        }
        CustomerOrder order = lock(payment.getOrder());

        if (payment.getStatus() != PaymentStatus.PAID) {

            if (paidSoFar(order).add(payment.getAmount()).compareTo(order.getGrandTotal()) > 0) {

                throw new BusinessRuleException("Paying this would exceed the total of order " + order.getId());
            }
            payment.markPaid();
        }
        payments.saveAndFlush(payment);

        if (order.getStatus() == OrderStatus.EXPIRED || order.getStatus() == OrderStatus.CANCELLED) {

            return SettleOutcome.REFUND_NEEDED;
        }
        if (paidSoFar(order).compareTo(order.getGrandTotal()) >= 0) {

            orderService.confirmAfterPayment(order);
        }
        return SettleOutcome.RECORDED;
    }

    /**
     * The provider returned the money (called by the refund flow and the refund
     * webhook).
     */
    public void markRefunded(Payment payment) {
        if (payment.getStatus() != PaymentStatus.REFUNDED) {
            payment.markRefunded();
            payments.saveAndFlush(payment);
        }
    }

    /**
     * Re-reads the order and locks its row, so its status cannot change under a
     * payment decision.
     */
    private CustomerOrder lock(CustomerOrder order) {

        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        return order;
    }

    private BigDecimal paidSoFar(CustomerOrder order) {

        return payments.sumAmountByOrderAndStatus(order.getId(),
                PaymentStatus.PAID);
    }
}
