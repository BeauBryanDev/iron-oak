package com.ironoak.dto.response;

import com.ironoak.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        String provider,
        String providerReference,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt) {
}
