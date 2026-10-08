package com.ironoak.dto.response;

import com.ironoak.domain.enums.PricingType;

import java.math.BigDecimal;

/** Prices are starting prices; priceUnit says what they cover ("visit", "hour", "day"). */
public record ServiceOfferingResponse(
        Long id,
        String code,
        String name,
        String category,
        String description,
        PricingType pricingType,
        BigDecimal fixedPrice,
        BigDecimal hourlyRate,
        BigDecimal estimatedMinHours,
        BigDecimal estimatedMaxHours,
        String priceUnit) {
}
