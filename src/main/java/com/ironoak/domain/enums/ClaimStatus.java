package com.ironoak.domain.enums;

/** Maps to the PostgreSQL enum type {@code claim_status}. */
public enum ClaimStatus {
    OPEN,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    RESOLVED
}
