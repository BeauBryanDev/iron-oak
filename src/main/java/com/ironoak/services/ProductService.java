package com.ironoak.services;

import com.ironoak.dto.response.ProductResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.ProductMapper;
import com.ironoak.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;
    private final ProductMapper mapper;

    public ProductService(ProductRepository products,
            ProductMapper mapper) {
        this.products = products;
        this.mapper = mapper;
    }

    /**
     * Active products, optionally narrowed by storefront category or a name search.
     */
    public Page<ProductResponse> list(String category,
            String search,
            Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return products.findByNameContainingIgnoreCaseAndIsActiveTrue(search.trim(), pageable)
                    .map(mapper::toResponse);
        }
        if (category != null && !category.isBlank()) {
            return products.findByCategoryAndIsActiveTrue(category, pageable).map(mapper::toResponse);
        }
        return products.findByIsActiveTrue(pageable).map(mapper::toResponse);
    }

    public ProductResponse getById(Long id) {
        return products.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    public ProductResponse getBySku(String sku) {
        return products.findBySku(sku)
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", sku));
    }

    public List<String> categories() {
        return products.findActiveCategories();
    }

    /**
     * Products matching a vision prediction label (a tool_category.model_label).
     */
    public List<ProductResponse> findByVisionLabel(String modelLabel) {
        return mapper.toResponses(products.findByVisionNameAndIsActiveTrue(modelLabel));
    }
}
