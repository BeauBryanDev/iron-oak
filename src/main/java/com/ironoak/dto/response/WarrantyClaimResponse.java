package com.ironoak.dto.response;

import com.ironoak.domain.enums.ClaimStatus;

import java.time.OffsetDateTime;

public record WarrantyClaimResponse(
        Long id,
        String claimNumber,
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
