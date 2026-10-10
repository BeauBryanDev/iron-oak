package com.ironoak.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.ironoak.domain.enums.AuditAction;

import java.time.OffsetDateTime;

/** One audit entry; oldValue/newValue hold only the fields that changed. */
public record AuditLogResponse(
        Long id,
        OffsetDateTime createdAt,
        Long adminUserId,
        String username,
        AuditAction action,
        String resourceType,
        String resourceId,
        JsonNode oldValue,
        JsonNode newValue,
        String ipAddress,
        String userAgent) {
}
