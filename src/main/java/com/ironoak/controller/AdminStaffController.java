package com.ironoak.controller;

import com.ironoak.dto.request.CreateStaffRequest;
import com.ironoak.dto.request.ResetStaffPasswordRequest;
import com.ironoak.dto.request.UpdateActiveRequest;
import com.ironoak.dto.response.StaffResponse;
import com.ironoak.services.AdminStaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * Admin accounts: any admin can list, create, disable/enable and reset other
 * admins.
 */
@RestController
@RequestMapping("/api/admin/staff")
public class AdminStaffController {

    private final AdminStaffService staff;

    public AdminStaffController(AdminStaffService staff) {
        this.staff = staff;
    }

    @GetMapping
    public List<StaffResponse> list() {

        return staff.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@Valid @RequestBody CreateStaffRequest request) {

        return staff.create(request);
    }

    @PatchMapping("/{id}/active")
    public StaffResponse setActive(@PathVariable Long id,
            @Valid @RequestBody UpdateActiveRequest request,
            Principal principal) {

        return staff.setActive(id, request.isActive(),
                principal.getName());
    }

    @PostMapping("/{id}/reset-password")
    public StaffResponse resetPassword(@PathVariable Long id,
            @Valid @RequestBody ResetStaffPasswordRequest request,
            Principal principal) {

        return staff.resetPassword(id, request.temporaryPassword(),
                principal.getName());
    }
}
