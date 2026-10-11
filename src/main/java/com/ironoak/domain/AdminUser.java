package com.ironoak.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

/**
 * Staff login for the dashboard. Deliberately separate from {@link Customer}:
 * buyers
 * never authenticate, so there is no shared account hierarchy between the two.
 */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    @NotBlank
    @Size(max = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 200)
    @NotBlank
    @Size(max = 200)
    private String email;

    /** BCrypt hash - never the raw password. */
    @Column(name = "password_hash", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 200)
    @Size(max = 200)
    private String fullName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /** Access tokens issued before this moment are no longer accepted. */
    @Column(name = "password_changed_at", nullable = false)
    private OffsetDateTime passwordChangedAt;

    /** False = disabled (V12): cannot log in, tokens are rejected. Never deleted. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** True until the person replaces a temporary password (V12). */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    protected AdminUser() {
    }

    public AdminUser(String username,
            String email,
            String passwordHash,
            String fullName) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.createdAt = OffsetDateTime.now();
        this.passwordChangedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    /** Stores a new hash and invalidates every access token issued before now. */
    /** The person chose this password themselves; clears the forced-change flag. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        this.mustChangePassword = false;
    }

    /**
     * Someone else set this password (bootstrap, new staff, reset): older tokens stop working
     * and the account is limited to changing it until the person picks their own.
     */
    public void setTemporaryPassword(String temporaryPasswordHash) {
        this.passwordHash = temporaryPasswordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        this.mustChangePassword = true;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void setPasswordChangedAt(OffsetDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
