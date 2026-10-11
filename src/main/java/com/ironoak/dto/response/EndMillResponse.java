package com.ironoak.dto.response;

import com.ironoak.domain.EndMill;

import java.math.BigDecimal;

/** Public (storefront and Piper) view of an end mill: no stock count, no vision label. */
public record EndMillResponse(
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
        boolean inStock,
        String imageUrl) {

    public static EndMillResponse from(EndMill e) {
        return new EndMillResponse(e.getId(), e.getSku(), e.getName(), e.getDescription(), e.getFluteType(),
                e.getCutDirection(), e.getCuttingDiameterMm(), e.getRadiusMm(), e.getMaterial(),
                e.getOverallLengthMm(), e.getWeightG(), e.getPrice(), e.getMaxRpm(), e.isInStock(),
                e.getImageUrl());
    }
}
