package com.ironoak.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Staff login for the dashboard. Deliberately separate from {@link Customer}: buyers
 * never authenticate, so there is no shared account hierarchy between the two.
 */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    /** BCrypt hash - never the raw password. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    protected AdminUser() {
    }

    public AdminUser(String username, String email, String passwordHash, String fullName) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.createdAt = OffsetDateTime.now();
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

    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
    }
}
