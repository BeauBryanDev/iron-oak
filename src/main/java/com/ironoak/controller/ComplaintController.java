package com.ironoak.controller;

import com.ironoak.dto.request.ComplaintFilter;
import com.ironoak.domain.enums.ComplaintStatus;
import com.ironoak.dto.request.ComplaintRequest;
import com.ironoak.dto.request.UpdateComplaintStatusRequest;
import com.ironoak.dto.response.ComplaintResponse;
import com.ironoak.services.ComplaintService;
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

/**
 * Filing a complaint is public (POST /api/complaints); reviewing them is
 * staff-only.
 */
@RestController
public class ComplaintController {

    private final ComplaintService complaints;

    public ComplaintController(ComplaintService complaints) {
        this.complaints = complaints;
    }

    @PostMapping("/api/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintResponse file(@Valid @RequestBody ComplaintRequest request) {

        return complaints.create(request);
    }

    @GetMapping("/api/admin/complaints")
    public PagedModel<ComplaintResponse> list(
            ComplaintFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return new PagedModel<>(complaints.list(filter, pageable));
    }

    @PatchMapping("/api/admin/complaints/{id}/status")
    public ComplaintResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateComplaintStatusRequest request) {

        return complaints.updateStatus(id, request.status());
    }
}
