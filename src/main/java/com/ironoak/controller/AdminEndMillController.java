package com.ironoak.controller;

import com.ironoak.dto.request.AdjustStockRequest;
import com.ironoak.dto.request.EndMillRequest;
import com.ironoak.dto.request.UpdateActiveRequest;
import com.ironoak.dto.response.AdminEndMillResponse;
import com.ironoak.services.AdminEndMillService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
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

/**
 * Staff end-mill catalog: list (with inactive ones), read, create, edit,
 * activate/deactivate and
 * adjust stock. Rows are never deleted.
 */
@RestController
@RequestMapping("/api/admin/end-mills")
public class AdminEndMillController {

    private final AdminEndMillService endMills;

    public AdminEndMillController(AdminEndMillService endMills) {
        this.endMills = endMills;
    }

    @GetMapping
    public PagedModel<AdminEndMillResponse> list(
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(required = false) String fluteType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer maxStock,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {

        return new PagedModel<>(endMills.list(search, fluteType,
                active, maxStock, pageable));
    }

    @GetMapping("/{id}")
    public AdminEndMillResponse get(@PathVariable Long id) {

        return endMills.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminEndMillResponse create(@Valid @RequestBody EndMillRequest request) {

        return endMills.create(request);
    }

    @PutMapping("/{id}")
    public AdminEndMillResponse update(@PathVariable Long id, @Valid @RequestBody EndMillRequest request) {

        return endMills.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminEndMillResponse setActive(@PathVariable Long id, @Valid @RequestBody UpdateActiveRequest request) {

        return endMills.setActive(id, request.isActive());
    }

    @PatchMapping("/{id}/stock")
    public AdminEndMillResponse adjustStock(@PathVariable Long id, @Valid @RequestBody AdjustStockRequest request) {

        return endMills.adjustStock(id, request.delta());
    }
}
