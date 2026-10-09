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
        return ProductResponse.from(product);
    }

    public List<ProductResponse> toResponses(List<Product> products) {

        if (products == null || products.isEmpty()) {

            return List.of();
        }

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
