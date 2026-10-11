package com.ironoak.dto.response;

import com.ironoak.domain.AdminUser;

import java.time.OffsetDateTime;

/**
 * An admin account as other admins see it; never includes the password hash.
 */
public record StaffResponse(
        Long id,
        String username,
        String email,
        String fullName,
        boolean active,
        boolean passwordChangeRequired,
        OffsetDateTime createdAt,
        OffsetDateTime lastLoginAt) {

    public static StaffResponse from(AdminUser admin) {
        return new StaffResponse(admin.getId(),
                admin.getUsername(),
                admin.getEmail(),
                admin.getFullName(),
                admin.isActive(),
                admin.isMustChangePassword(),
                admin.getCreatedAt(),
                admin.getLastLoginAt());
    }
}
