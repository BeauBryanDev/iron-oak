package com.ironoak.dto.response;

import com.ironoak.domain.EndMill;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Staff view of an end mill: adds stock, vision label, active flag and timestamps. */
public record AdminEndMillResponse(
        Long id,
        String sku,
        String name,
        String description,
        String fluteType,
        String cutDirection,
        BigDecimal cuttingDiameterMm,
        BigDecimal radiusMm,
        String material,
        BigDecimal overallLengthMm,
        BigDecimal weightG,
        BigDecimal price,
        Integer maxRpm,
        int stockQuantity,
        String visionLabel,
        String imageUrl,
        boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static AdminEndMillResponse from(EndMill e) {
        return new AdminEndMillResponse(e.getId(), e.getSku(), e.getName(), e.getDescription(), e.getFluteType(),
                e.getCutDirection(), e.getCuttingDiameterMm(), e.getRadiusMm(), e.getMaterial(),
                e.getOverallLengthMm(), e.getWeightG(), e.getPrice(), e.getMaxRpm(), e.getStockQuantity(),
                e.getVisionLabel(), e.getImageUrl(), Boolean.TRUE.equals(e.getIsActive()), e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
