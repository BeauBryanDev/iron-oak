package com.ironoak.dto.response;

import com.ironoak.domain.enums.ClaimStatus;

import java.time.OffsetDateTime;

public record WarrantyClaimResponse(
        Long id,
        ClaimStatus status,
        String customerName,
        Long orderId,
        Long orderItemId,
        String description,
        String resolutionNote,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime resolvedAt) {
}
