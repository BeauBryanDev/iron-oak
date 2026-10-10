package com.ironoak.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** visionName is not accepted: it is always taken from the tool category's model label. */
public record CreateProductRequest(
        @NotNull Long toolCategoryId,
        @NotBlank @Size(max = 50) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String brand,
        @NotBlank @Size(max = 50) String category,
        String description,
        @NotNull @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer stockQuantity,
        @NotNull @Min(0) Integer warrantyMonths,
        @Positive @Digits(integer = 5, fraction = 3) BigDecimal weightKg,
        @Positive @Digits(integer = 4, fraction = 4) BigDecimal volumeM3,
        @Size(max = 500) String imageUrl,
        Boolean isActive) {
}
