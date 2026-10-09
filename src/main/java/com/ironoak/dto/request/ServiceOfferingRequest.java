package com.ironoak.dto.request;

import com.ironoak.domain.enums.PricingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Used for both create and update. Which price fields are required depends on pricingType
 * (FIXED: fixedPrice + priceUnit; HOURLY: hourlyRate, both hour bounds + priceUnit; QUOTE:
 * none); the service enforces that and clears the fields that do not apply.
 */
public record ServiceOfferingRequest(
        @NotNull Long serviceOfferingCategoryId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotNull PricingType pricingType,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal fixedPrice,
        @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal hourlyRate,
        @DecimalMin("0.0") @Digits(integer = 3, fraction = 1) BigDecimal estimatedMinHours,
        @DecimalMin("0.0") @Digits(integer = 3, fraction = 1) BigDecimal estimatedMaxHours,
        @Size(max = 30) String priceUnit,
        Boolean isActive) {
}
