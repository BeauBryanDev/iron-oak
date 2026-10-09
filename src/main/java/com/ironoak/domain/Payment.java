package com.ironoak.domain;

import com.ironoak.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Written by the checkout flow, never by Piper. */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "payment_status")
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(length = 50)
    private String provider;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    protected Payment() {
    }

    public Payment(CustomerOrder order, BigDecimal amount, String provider, String providerReference) {
        this.order = order;
        this.amount = amount;
        this.provider = provider;
        this.providerReference = providerReference;
    }

    public void markPaid() {
        this.status = PaymentStatus.PAID;
        this.paidAt = OffsetDateTime.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }
}
