package com.ironoak.dto.request;

import com.ironoak.domain.enums.AuditAction;

import java.time.LocalDate;
import java.util.List;

/**
 * Query parameters of GET /api/admin/audit: repeatable action, the admin's username, the
 * resource (type and id, e.g. PRODUCT / 12), inclusive UTC dates and free text q (username,
 * resource id or IP).
 */
public record AuditFilter(
        List<AuditAction> action,
        String username,
        String resourceType,
        String resourceId,
        LocalDate from,
        LocalDate to,
        String q) {
}
