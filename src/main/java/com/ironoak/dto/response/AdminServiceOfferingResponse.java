package com.ironoak.dto.response;

import com.ironoak.domain.enums.PricingType;

import java.math.BigDecimal;

public record AdminServiceOfferingResponse(
        Long id,
        String code,
        String name,
        Long categoryId,
        String category,
        String description,
        PricingType pricingType,
        BigDecimal fixedPrice,
        BigDecimal hourlyRate,
        BigDecimal estimatedMinHours,
        BigDecimal estimatedMaxHours,
        String priceUnit,
        boolean isActive) {
}
