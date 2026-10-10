package com.ironoak.dto.response;

import java.math.BigDecimal;

public record AdminMillingMachineResponse(
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
        String imageUrl,
        boolean isActive) {
}
