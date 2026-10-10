package com.ironoak.controller;

import com.ironoak.dto.request.AuditFilter;
import com.ironoak.dto.response.AuditLogResponse;
import com.ironoak.services.AuditService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin audit trail, newest first. Read-only: entries cannot be edited or
 * deleted.
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AdminAuditController {

    private final AuditService audit;

    public AdminAuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PagedModel<AuditLogResponse> list(
            AuditFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return new PagedModel<>(audit.list(filter, pageable));
    }
}
