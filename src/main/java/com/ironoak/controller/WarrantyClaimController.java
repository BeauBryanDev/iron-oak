package com.ironoak.controller;

import com.ironoak.dto.request.WarrantyClaimFilter;
import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.dto.request.CreateWarrantyClaimRequest;
import com.ironoak.dto.request.UpdateWarrantyClaimStatusRequest;
import com.ironoak.dto.response.WarrantyClaimResponse;
import com.ironoak.services.WarrantyClaimService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Customers file and follow claims with their email (?email=); staff review under /api/admin. */
@RestController
public class WarrantyClaimController {

    private final WarrantyClaimService claims;

    public WarrantyClaimController(WarrantyClaimService claims) {
        this.claims = claims;
    }

    @PostMapping("/api/warranty-claims")
    @ResponseStatus(HttpStatus.CREATED)
    public WarrantyClaimResponse file(@Valid @RequestBody CreateWarrantyClaimRequest request) {
        return claims.create(request);
    }

    @GetMapping("/api/warranty-claims")
    public List<WarrantyClaimResponse> listMine(@RequestParam String email) {
        return claims.listMine(email);
    }

    @GetMapping("/api/warranty-claims/{id}")
    public WarrantyClaimResponse get(@PathVariable Long id, @RequestParam String email) {
        return claims.get(id, email);
    }

    @GetMapping("/api/admin/warranty-claims")
    public PagedModel<WarrantyClaimResponse> list(
            WarrantyClaimFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(claims.list(filter, pageable));
    }

    @GetMapping("/api/admin/warranty-claims/{id}")
    public WarrantyClaimResponse getForStaff(@PathVariable Long id) {
        return claims.getForStaff(id);
    }

    @PatchMapping("/api/admin/warranty-claims/{id}/status")
    public WarrantyClaimResponse updateStatus(@PathVariable Long id,
                                              @Valid @RequestBody UpdateWarrantyClaimStatusRequest request) {
        return claims.updateStatus(id, request);
    }
}
