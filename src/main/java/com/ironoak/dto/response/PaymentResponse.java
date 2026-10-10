package com.ironoak.dto.response;

import com.ironoak.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String provider,
        String providerReference,
        String checkoutSessionId,
        String paymentIntentId,
        String failureCode,
        String failureMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime paidAt) {
}
