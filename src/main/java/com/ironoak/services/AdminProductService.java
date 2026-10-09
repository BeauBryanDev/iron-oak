package com.ironoak.services;

import com.ironoak.domain.Product;
import com.ironoak.domain.ToolCategory;
import com.ironoak.dto.request.CreateProductRequest;
import com.ironoak.dto.request.UpdateProductRequest;
import com.ironoak.dto.response.AdminProductResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.DuplicateResourceException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.AdminCatalogMapper;
import com.ironoak.repository.ProductRepository;
import com.ironoak.repository.ToolCategoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Staff catalog management for products. Rows are deactivated, never deleted
 * (orders reference them).
 */
@Service
@Transactional
public class AdminProductService {

    private final ProductRepository products;
    private final ToolCategoryRepository toolCategories;
    private final AdminCatalogMapper mapper;
    private final EntityManager entityManager;

    public AdminProductService(ProductRepository products,
            ToolCategoryRepository toolCategories,
            AdminCatalogMapper mapper,
            EntityManager entityManager) {

        this.products = products;
        this.toolCategories = toolCategories;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    /** All products including inactive ones; each filter is optional. */
    @Transactional(readOnly = true)
    public Page<AdminProductResponse> list(String search, String category, Long toolCategoryId, Boolean active,
                                           Integer maxStock, Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                filters.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("sku")), pattern),
                        cb.like(cb.lower(root.get("brand")), pattern)));
            }
            if (category != null && !category.isBlank()) {
                filters.add(cb.equal(root.get("category"), category.trim()));
            }
            if (toolCategoryId != null) {
                filters.add(cb.equal(root.get("toolCategory").get("id"), toolCategoryId));
            }
            if (active != null) {
                filters.add(cb.equal(root.get("isActive"), active));
            }
            if (maxStock != null) {
                filters.add(cb.lessThanOrEqualTo(root.get("stockQuantity"), maxStock)); // low-stock view
            }
            return cb.and(filters.toArray(new Predicate[0]));
        };
        return products.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminProductResponse get(Long id) {
        return mapper.toResponse(find(id));
    }

    public AdminProductResponse create(CreateProductRequest request) {
        String sku = request.sku().trim();
        if (products.findBySku(sku).isPresent()) {
            throw new DuplicateResourceException("Product", "sku", sku);
        }
        ToolCategory toolCategory = findToolCategory(request.toolCategoryId());
        Product product = new Product(toolCategory, sku, toolCategory.getModelLabel(),
                request.name().trim(), request.category().trim(), request.price(), request.stockQuantity());
        product.setBrand(blankToNull(request.brand()));
        product.setDescription(blankToNull(request.description()));
        product.setWarrantyMonths(request.warrantyMonths());
        product.setImageUrl(blankToNull(request.imageUrl()));
        product.setIsActive(request.isActive() == null || request.isActive());
        products.saveAndFlush(product);
        entityManager.refresh(product); // created_at is filled by the database
        return mapper.toResponse(product);
    }

    public AdminProductResponse update(Long id, UpdateProductRequest request) {
        Product product = find(id);
        String sku = request.sku().trim();
        products.findBySku(sku)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Product", "sku", sku);
                });
        if (!product.getToolCategory().getId().equals(request.toolCategoryId())) {
            ToolCategory toolCategory = findToolCategory(request.toolCategoryId());
            product.setToolCategory(toolCategory);
            product.setVisionName(toolCategory.getModelLabel()); // the composite FK ties the pair together
        }
        product.setSku(sku);
        product.setName(request.name().trim());
        product.setBrand(blankToNull(request.brand()));
        product.setCategory(request.category().trim());
        product.setDescription(blankToNull(request.description()));
        product.setPrice(request.price());
        product.setWarrantyMonths(request.warrantyMonths());
        product.setImageUrl(blankToNull(request.imageUrl()));
        if (request.isActive() != null) {
            product.setIsActive(request.isActive());
        }
        products.saveAndFlush(product);
        return mapper.toResponse(product);
    }

    public AdminProductResponse setActive(Long id, boolean active) {
        Product product = find(id);
        product.setIsActive(active);
        products.saveAndFlush(product);
        return mapper.toResponse(product);
    }

    /** Atomic, so it cannot lose a concurrent sale; refuses to go below zero. */
    public AdminProductResponse adjustStock(Long id, int delta) {
        find(id);
        if (delta != 0 && products.adjustStock(id, delta) == 0) {
            throw new BusinessRuleException("Stock cannot go below zero");
        }
        return mapper.toResponse(find(id)); // adjustStock cleared the persistence context
    }

    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private ToolCategory findToolCategory(Long id) {
        return toolCategories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tool category", id));
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
