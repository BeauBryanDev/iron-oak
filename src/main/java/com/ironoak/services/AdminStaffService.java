package com.ironoak.services;

import com.ironoak.domain.AdminUser;
import com.ironoak.domain.enums.AuditAction;
import com.ironoak.dto.request.CreateStaffRequest;
import com.ironoak.dto.response.StaffResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.DuplicateResourceException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin accounts, managed by any admin. New accounts and resets get a temporary
 * password the
 * person must replace at first login. Accounts are disabled, never deleted (the
 * audit trail
 * keeps its actor); nobody can disable themselves or the last active admin.
 */
@Service
@Transactional
public class AdminStaffService {

    private final AdminUserRepository adminUsers;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;

    public AdminStaffService(AdminUserRepository adminUsers,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokens,
            AuditService audit) {

        this.adminUsers = adminUsers;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list() {
        return adminUsers.findAllByOrderByUsernameAsc().stream()
                .map(StaffResponse::from).toList();
    }

    public StaffResponse create(CreateStaffRequest request) {

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (adminUsers.findByUsername(username).isPresent()) {
            throw new DuplicateResourceException("Admin user", "username", username);
        }
        if (adminUsers.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateResourceException("Admin user", "email", email);
        }
        PasswordPolicy.validate(request.temporaryPassword(), username, null);

        AdminUser admin = new AdminUser(username, email, "",
                AdminProductService.blankToNull(request.fullName()));

        admin.setTemporaryPassword(passwordEncoder.encode(request.temporaryPassword()));

        StaffResponse created = StaffResponse.from(adminUsers.saveAndFlush(admin));

        audit.record(AuditAction.STAFF_CREATE, "ADMIN_USER",
                created.id(), null, created);

        return created;
    }

    /**
     * Disabling revokes every session at once; the account's access tokens stop
     * working too.
     */
    public StaffResponse setActive(Long id, boolean active,
            String actingUsername) {

        List<AdminUser> activeAdmins = adminUsers.lockActive();
        AdminUser admin = find(id);

        if (admin.isActive() == active) {
            return StaffResponse.from(admin);
        }
        if (!active) {
            if (admin.getUsername().equals(actingUsername)) {
                throw new BusinessRuleException("You cannot disable your own account");
            }
            if (activeAdmins.size() <= 1) {
                throw new BusinessRuleException("The last active admin cannot be disabled");
            }
        }
        admin.setActive(active);
        adminUsers.saveAndFlush(admin);

        if (!active) {
            refreshTokens.revokeAll(admin, "DISABLED");
        }
        audit.record(active ? AuditAction.STAFF_ENABLE : AuditAction.STAFF_DISABLE,
                "ADMIN_USER", id,
                Map.of("active", !active),
                Map.of("active", active));

        return StaffResponse.from(admin);
    }

    /**
     * For someone else's account; your own password goes through
     * /api/admin/auth/change-password.
     */
    public StaffResponse resetPassword(Long id, String temporaryPassword,
            String actingUsername) {

        AdminUser admin = find(id);
        if (admin.getUsername().equals(actingUsername)) {
            throw new BusinessRuleException("Use /api/admin/auth/change-password for your own account");
        }
        PasswordPolicy.validate(temporaryPassword, admin.getUsername(), null);
        admin.setTemporaryPassword(passwordEncoder.encode(temporaryPassword));

        adminUsers.saveAndFlush(admin);
        refreshTokens.revokeAll(admin, "PASSWORD_RESET");

        audit.record(AuditAction.STAFF_PASSWORD_RESET,
                "ADMIN_USER", id, null,
                Map.of("passwordChangeRequired", true)); // the password itself is never recorded

        return StaffResponse.from(admin);
    }

    private AdminUser find(Long id) {

        return adminUsers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Admin user", id));
    }
}
