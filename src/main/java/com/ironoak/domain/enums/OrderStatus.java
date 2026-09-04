package com.ironoak.domain.enums;

/** Maps to the PostgreSQL enum type {@code order_status}. */
public enum OrderStatus {
    DRAFT,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
