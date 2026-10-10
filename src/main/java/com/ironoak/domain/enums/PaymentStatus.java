package com.ironoak.domain.enums;

/** Maps to the PostgreSQL enum type {@code payment_status}. */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    PAID,
    FAILED,
    EXPIRED,
    CANCELLED,
    REFUNDED
}
