package com.ironoak.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Full replacement of the editable fields. Stock is deliberately absent: it changes only
 * through the atomic stock adjustment, so an edit form cannot overwrite sales that
 * happened while it was open. isActive null leaves the flag unchanged.
 */
public record UpdateProductRequest(
        @NotNull Long toolCategoryId,
        @NotBlank @Size(max = 50) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String brand,
        @NotBlank @Size(max = 50) String category,
        String description,
        @NotNull @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer warrantyMonths,
        @Size(max = 500) String imageUrl,
        Boolean isActive) {
}
