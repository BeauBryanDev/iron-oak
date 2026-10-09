package com.ironoak.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Used for both create and update. isActive null means active on create, unchanged on update. */
public record MillingMachineRequest(
        @NotBlank @Size(max = 30) String modelCode,
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotNull @Positive @Digits(integer = 4, fraction = 1) BigDecimal powerKw,
        @NotNull @Positive Integer spindleMinRpm,
        @NotNull @Positive Integer spindleMaxRpm,
        @NotNull @Positive Integer tableLengthMm,
        @NotNull @Positive Integer tableWidthMm,
        @NotNull @DecimalMin("0.0") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer warrantyMonths,
        @Size(max = 500) String imageUrl,
        Boolean isActive) {
}
