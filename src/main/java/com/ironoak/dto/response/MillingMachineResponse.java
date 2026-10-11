package com.ironoak.dto.response;

import java.math.BigDecimal;

/**
 * Public view of a milling machine. weightKg and volumeM3 are the packed shipping weight and
 * volume (V6); null until entered.
 */
public record MillingMachineResponse(
        Long id,
        String modelCode,
        String name,
        String description,
        BigDecimal powerKw,
        int spindleMinRpm,
        int spindleMaxRpm,
        int tableLengthMm,
        int tableWidthMm,
        BigDecimal price,
        int warrantyMonths,
        BigDecimal weightKg,
        BigDecimal volumeM3,
        String imageUrl) {
}
