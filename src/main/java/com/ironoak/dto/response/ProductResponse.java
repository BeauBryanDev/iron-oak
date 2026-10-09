package com.ironoak.dto.response;

import java.math.BigDecimal;

import com.ironoak.domain.Product;

public record ProductResponse(
                Long id,
                String sku,
                String name,
                String brand,
                String category,
                String visionName,
                String description,
                BigDecimal price,
                int stockQuantity,
                boolean inStock,
                int warrantyMonths,
                String imageUrl,
                String toolCategoryDisplayName,
                Long toolCategoryId

) {

        public static ProductResponse from(Product product) {
                return new ProductResponse(
                                product.getId(),
                                product.getSku(),
                                product.getName(),
                                product.getBrand(),
                                product.getCategory(),
                                product.getVisionName(),
                                product.getDescription(),
                                product.getPrice(),
                                product.getStockQuantity(),
                                product.isInStock(),
                                product.getWarrantyMonths(),
                                product.getImageUrl(),
                                product.getToolCategory() != null ? product.getToolCategory().getDisplayName() : null,
                                product.getToolCategory() != null ? product.getToolCategory().getId() : null);
        }
}
