package com.ironoak.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Staff view of a product: includes inactive state and creation time. */
public record AdminProductResponse(
        Long id,
        String sku,
        String name,
        String brand,
        String category,
        String description,
        BigDecimal price,
        int stockQuantity,
        int warrantyMonths,
        String imageUrl,
        boolean isActive,
        Long toolCategoryId,
        String toolCategoryDisplayName,
        String visionName,
        OffsetDateTime createdAt) {
}
