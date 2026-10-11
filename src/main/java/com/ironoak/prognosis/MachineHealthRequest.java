package com.ironoak.prognosis;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * One reading of a CNC process, in the units the model was trained on (AI4I
 * 2020).
 * Temperatures are in Kelvin; the bounds only reject physically impossible
 * values
 * (e.g. Celsius sent as Kelvin). qualityVariant is the dataset's product
 * quality
 * variant: L (low), M (medium), H (high).
 */
public record MachineHealthRequest(
        @NotNull @DecimalMin("250") @DecimalMax("350") Double airTempK,
        @NotNull @DecimalMin("250") @DecimalMax("400") Double processTempK,
        @NotNull @DecimalMin("1") @DecimalMax("10000") Double rpm,
        @NotNull @DecimalMin("0") @DecimalMax("500") Double torqueNm,
        @NotNull @DecimalMin("0") @DecimalMax("1000") Double toolWearMin,
        @NotNull QualityVariant qualityVariant) {

    public enum QualityVariant {
        L, M, H
    }
}
