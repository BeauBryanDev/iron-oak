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
        String imageUrl,
        boolean isActive) {
}
