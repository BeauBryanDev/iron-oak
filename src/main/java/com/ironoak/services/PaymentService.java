package com.ironoak.services;

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
import java.util.Set;

/**
 * Written by the checkout flow and payment-provider callbacks, never by Piper.
 * The
 * amount is always derived from the order, never taken from the client. Refunds
 * are not
 * modelled: payment_status has PENDING, PAID and FAILED only.
 */
@Service
@Transactional
public class PaymentService {

    private static final Set<OrderStatus> PAYABLE = Set.of(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS,
            OrderStatus.COMPLETED);

    private final PaymentRepository payments;
    private final CustomerOrderRepository orders;
    private final PaymentMapper mapper;

    public PaymentService(PaymentRepository payments, CustomerOrderRepository orders, PaymentMapper mapper) {
        this.payments = payments;
        this.orders = orders;
        this.mapper = mapper;
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
        BigDecimal due = order.getTotalAmount().subtract(paidSoFar(order));
        if (due.signum() <= 0) {
            throw new BusinessRuleException("Order " + order.getId() + " has nothing left to pay");
        }
        return mapper.toResponse(payments.saveAndFlush(new Payment(order, due, provider, reference)));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(PaymentFilter filter, Pageable pageable) {
        Specification<Payment> spec = Specification.allOf(
                FilterSpecs.in("status", filter.status()),
                FilterSpecs.hasText(filter.provider()) ? FilterSpecs.equal("provider", filter.provider().trim()) : null,
                FilterSpecs.dateRange("createdAt", filter.from(), filter.to()),
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
                    ? cb.or(reference, cb.equal(root.get("order").get("id"), Long.parseLong(trimmed)))
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
     * Only a PENDING payment can be settled, and total PAID never exceeds the order
     * total.
     */
    public PaymentResponse updateStatus(Long id, PaymentStatus newStatus) {
        Payment payment = payments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        if (payment.getStatus() != PaymentStatus.PENDING || newStatus == PaymentStatus.PENDING) {
            throw new BusinessRuleException(
                    "Cannot change payment " + id + " from " + payment.getStatus() + " to " + newStatus);
        }
        if (newStatus == PaymentStatus.PAID) {
            CustomerOrder order = payment.getOrder();
            if (paidSoFar(order).add(payment.getAmount()).compareTo(order.getTotalAmount()) > 0) {
                throw new BusinessRuleException("Paying this would exceed the total of order " + order.getId());
            }
            payment.markPaid();
        } else {
            payment.markFailed();
        }
        return mapper.toResponse(payments.saveAndFlush(payment));
    }

    private BigDecimal paidSoFar(CustomerOrder order) {
        return payments.sumAmountByOrderAndStatus(order.getId(), PaymentStatus.PAID);
    }
}
