package com.ironoak.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Staff create/update of an end mill (mirrors the V13 checks). stockQuantity is
 * for create only
 * (default 0) and must be omitted on update (422): afterwards stock changes
 * only through
 * PATCH .../stock. isActive null = unchanged
 * on update, true on create. radiusMm must not exceed half the cutting diameter
 * (checked in code).
 */
public record EndMillRequest(
                @NotBlank @Size(max = 50) String sku,
                @NotBlank @Size(max = 255) String name,
                String description,
                @NotBlank @Size(max = 100) String fluteType,
                @Pattern(regexp = "Right-Hand|Left-Hand", message = "must be Right-Hand or Left-Hand") String cutDirection,
                @NotNull @Positive @Digits(integer = 3, fraction = 2) BigDecimal cuttingDiameterMm,
                @NotNull @PositiveOrZero @Digits(integer = 3, fraction = 2) BigDecimal radiusMm,
                @NotBlank @Size(max = 100) String material,
                @NotNull @Positive @Digits(integer = 5, fraction = 2) BigDecimal overallLengthMm,
                @Positive @Digits(integer = 5, fraction = 2) BigDecimal weightG,
                @NotNull @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal price,
                @Positive Integer maxRpm,
                @Min(0) Integer stockQuantity,
                @Pattern(regexp = "[a-z0-9_]{1,100}", message = "must be lowercase letters, digits and underscores") String visionLabel,
                @Size(max = 500) String imageUrl,
                Boolean isActive) {
}
