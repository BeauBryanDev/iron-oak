package com.ironoak.controller;

import com.ironoak.domain.enums.PricingType;
import com.ironoak.dto.request.ServiceOfferingRequest;
import com.ironoak.dto.request.UpdateActiveRequest;
import com.ironoak.dto.request.UpdateToolCategoryRequest;
import com.ironoak.dto.response.AdminServiceOfferingResponse;
import com.ironoak.dto.response.ServiceCategoryResponse;
import com.ironoak.dto.response.ToolCategoryResponse;
import com.ironoak.services.AdminServiceOfferingService;
import com.ironoak.services.AdminToolCategoryService;
import com.ironoak.services.ToolCategoryService;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * Staff management of technical services, their categories, and tool-category
 * display text.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminServiceOfferingController {

    private final AdminServiceOfferingService services;
    private final AdminToolCategoryService adminToolCategories;
    private final ToolCategoryService toolCategories;

    public AdminServiceOfferingController(AdminServiceOfferingService services,
            AdminToolCategoryService adminToolCategories,
            ToolCategoryService toolCategories) {
        this.services = services;
        this.adminToolCategories = adminToolCategories;
        this.toolCategories = toolCategories;
    }

    @GetMapping("/services")
    public List<AdminServiceOfferingResponse> list(
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) PricingType pricingType,
            @RequestParam(required = false) Boolean active) {
        return services.list(search, categoryId, pricingType, active);
    }

    @GetMapping("/services/{id}")
    public AdminServiceOfferingResponse get(@PathVariable Long id) {
        return services.get(id);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminServiceOfferingResponse create(@Valid @RequestBody ServiceOfferingRequest request) {
        return services.create(request);
    }

    @PutMapping("/services/{id}")
    public AdminServiceOfferingResponse update(@PathVariable Long id,
            @Valid @RequestBody ServiceOfferingRequest request) {
        return services.update(id, request);
    }

    @PatchMapping("/services/{id}/active")
    public AdminServiceOfferingResponse setActive(@PathVariable Long id,
            @Valid @RequestBody UpdateActiveRequest request) {
        return services.setActive(id, request.isActive());
    }

    @GetMapping("/service-categories")
    public List<ServiceCategoryResponse> serviceCategories() {
        return services.listCategories();
    }

    @GetMapping("/tool-categories")
    public List<ToolCategoryResponse> toolCategories() {
        return toolCategories.list();
    }

    @PutMapping("/tool-categories/{id}")
    public ToolCategoryResponse updateToolCategory(@PathVariable Long id,
            @Valid @RequestBody UpdateToolCategoryRequest request) {
        return adminToolCategories.update(id, request);
    }
}
