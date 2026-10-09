package com.ironoak.dto.response;

import com.ironoak.domain.enums.BookingStatus;

import java.time.OffsetDateTime;

public record BookingResponse(
        Long id,
        BookingStatus status,
        String serviceCode,
        String serviceName,
        String customerName,
        String locationAddress,
        OffsetDateTime scheduledAt,
        String machineModel,
        String notes,
        String cancelReason,
        Long orderId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
