package com.ironoak.mapper;

import com.ironoak.domain.Product;
import com.ironoak.domain.ToolCategory;
import com.ironoak.dto.response.ProductResponse;
import com.ironoak.dto.response.ToolCategoryResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
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
                product.getStockQuantity() > 0,
                product.getWarrantyMonths(),
                product.getImageUrl());
    }

    public List<ProductResponse> toResponses(List<Product> products) {
        return products.stream().map(this::toResponse).toList();
    }

    public ToolCategoryResponse toCategoryResponse(ToolCategory category) {
        return new ToolCategoryResponse(
                category.getId(),
                category.getModelLabel(),
                category.getDisplayName(),
                category.getSynonyms(),
                category.getDescription());
    }
}
