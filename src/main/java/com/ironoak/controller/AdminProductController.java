package com.ironoak.controller;

import com.ironoak.dto.request.AdjustStockRequest;
import com.ironoak.dto.request.CreateProductRequest;
import com.ironoak.dto.request.UpdateActiveRequest;
import com.ironoak.dto.request.UpdateProductRequest;
import com.ironoak.dto.response.AdminProductResponse;
import com.ironoak.services.AdminProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff product management (ROLE_ADMIN via /api/admin/**). Products are deactivated, not deleted. */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminProductService products;

    public AdminProductController(AdminProductService products) {
        this.products = products;
    }

    @GetMapping
    public PagedModel<AdminProductResponse> list(
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return new PagedModel<>(products.list(search, category, active, pageable));
    }

    @GetMapping("/{id}")
    public AdminProductResponse get(@PathVariable Long id) {
        return products.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return products.create(request);
    }

    @PutMapping("/{id}")
    public AdminProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminProductResponse setActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {
        return products.setActive(id, request.isActive());
    }

    @PatchMapping("/{id}/stock")
    public AdminProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody AdjustStockRequest request) {
        return products.adjustStock(id, request.delta());
    }
}
