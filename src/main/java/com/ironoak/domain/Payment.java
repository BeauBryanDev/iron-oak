package com.ironoak.domain;

import com.ironoak.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One payment attempt against an order. Written by the checkout flow and
 * provider callbacks,
 * never by Piper. The provider ids (checkout session, payment intent) are
 * unique when set, so
 * a replayed webhook finds the same row instead of creating a second payment.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @NotNull
    private CustomerOrder order;

    @Column(nullable = false, precision = 12, scale = 2)
    @NotNull
    @Positive
    private BigDecimal amount;

    /** ISO 4217, upper case; copied from the order. */
    @Column(nullable = false, length = 3)
    @NotNull
    @Pattern(regexp = "[A-Z]{3}")
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "payment_status")
    @NotNull
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(length = 50)
    @Size(max = 50)
    private String provider;

    @Column(name = "provider_reference", length = 100)
    @Size(max = 100)
    private String providerReference;

    @Column(name = "checkout_session_id", length = 255)
    @Size(max = 255)
    private String checkoutSessionId;

    @Column(name = "payment_intent_id", length = 255)
    @Size(max = 255)
    private String paymentIntentId;

    @Column(name = "idempotency_key", length = 100)
    @Size(max = 100)
    private String idempotencyKey;

    @Column(name = "failure_code", length = 100)
    @Size(max = 100)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    protected Payment() {
    }

    public Payment(CustomerOrder order,
            BigDecimal amount,
            String provider,
            String providerReference) {
        this.order = order;
        this.amount = amount;
        this.currency = order.getCurrency();
        this.provider = provider;
        this.providerReference = providerReference;
    }

    public void markProcessing() {
        this.status = PaymentStatus.PROCESSING;
    }

    public void markPaid() {
        this.status = PaymentStatus.PAID;
        this.paidAt = OffsetDateTime.now();
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    /** code and message come from the provider (for example a card decline). */
    public void markFailed(String code, String message) {
        markFailed();
        this.failureCode = code;
        this.failureMessage = message;
    }

    public void markExpired() {
        this.status = PaymentStatus.EXPIRED;
    }

    public void markCancelled() {
        this.status = PaymentStatus.CANCELLED;
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getCheckoutSessionId() {
        return checkoutSessionId;
    }

    public void setCheckoutSessionId(String checkoutSessionId) {
        this.checkoutSessionId = checkoutSessionId;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(String paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(OffsetDateTime paidAt) {
        this.paidAt = paidAt;
    }
}
