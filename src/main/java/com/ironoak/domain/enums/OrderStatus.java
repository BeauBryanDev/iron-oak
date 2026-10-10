package com.ironoak.domain.enums;

/**
 * Maps to the PostgreSQL enum type {@code order_status}.
 *
 * A web checkout starts in PENDING_PAYMENT; payment moves it to CONFIRMED. DRAFT is kept
 * for legacy rows.
 */
public enum OrderStatus {
    DRAFT,
    PENDING_PAYMENT,
    PAYMENT_FAILED,
    EXPIRED,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
