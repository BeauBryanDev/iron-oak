package com.ironoak.services;

import com.ironoak.config.JwtProperties;
import com.ironoak.domain.AdminUser;
import com.ironoak.domain.enums.AuditAction;
import com.ironoak.dto.request.AdminLoginRequest;
import com.ironoak.dto.request.ChangePasswordRequest;
import com.ironoak.dto.response.AdminLoginResponse;
import com.ironoak.dto.response.AdminProfileResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.AdminUserRepository;
import com.ironoak.security.AuditLog;
import com.ironoak.security.JwtService;
import com.ironoak.security.LoginAttemptTracker;
import com.ironoak.security.PasswordPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff sign-in, session refresh and logout, and password change. */
@Service
public class AdminAuthService {

    private final AuthenticationManager authenticationManager;
    private final AdminUserRepository adminUsers;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptTracker attempts;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final AuditService audit;

    public AdminAuthService(AuthenticationManager authenticationManager,
            AdminUserRepository adminUsers,
            JwtService jwtService,
            RefreshTokenService refreshTokens,
            LoginAttemptTracker attempts,
            PasswordEncoder passwordEncoder,
            JwtProperties jwtProperties,
            AuditService audit) {

        this.authenticationManager = authenticationManager;
        this.adminUsers = adminUsers;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.attempts = attempts;
        this.passwordEncoder = passwordEncoder;
        this.jwtProperties = jwtProperties;
        this.audit = audit;
    }

    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request,
            String ip,
            String userAgent) {

        String username = request.username().trim();
        attempts.assertNotLocked(username, ip); // 429 before any password work

        try {
            // A wrong password and an unknown username fail identically
            // so the response cannot be used to enumerate accounts.
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));

        } catch (AuthenticationException e) {

            attempts.recordFailure(username, ip);
            AuditLog.warn("login failed", username, ip);
            audit.recordAuth(AuditAction.LOGIN_FAILED,
                    username, ip, userAgent);

            throw new BadCredentialsException("Invalid credentials");
        }

        AdminUser admin = adminUsers.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        attempts.recordSuccess(username, ip);
        admin.recordLogin();
        AuditLog.info("login ok", username, ip);
        audit.recordAuth(AuditAction.LOGIN_SUCCESS,
                username, ip, userAgent);

        return respond(refreshTokens.startSession(admin, ip, userAgent));
    }

    /**
     * Deliberately not @Transactional: rotate() owns its transaction and keeps the
     * family
     * revocation it does on token reuse; an outer transaction would roll that back.
     */
    public AdminLoginResponse refresh(String refreshToken,
            String ip,
            String userAgent) {

        return respond(refreshTokens.rotate(refreshToken, ip, userAgent));
    }

    public void logout(String refreshToken, String ip) {

        refreshTokens.revokeSession(refreshToken, ip);
    }

    /** Ends every session of this account on every device. */
    @Transactional
    public void logoutAll(String username, String ip) {

        refreshTokens.revokeAll(find(username), "LOGOUT_ALL");
        AuditLog.info("logout all sessions", username, ip);
        audit.recordAuth(AuditAction.LOGOUT_ALL, username, ip, null);
    }

    @Transactional(readOnly = true)
    public AdminProfileResponse me(String username) {

        AdminUser admin = find(username);
        return new AdminProfileResponse(admin.getUsername(),
                admin.getEmail(), admin.getFullName(),
                admin.getLastLoginAt());
    }

    /**
     * Needs the current password, which is throttled like a login so a stolen
     * access token
     * cannot be used to guess it. On success every older session and access token
     * stops working
     * and a fresh session is returned for the caller.
     */
    @Transactional
    public AdminLoginResponse changePassword(String username,
            ChangePasswordRequest request,
            String ip,
            String userAgent) {

        attempts.assertNotLocked(username, ip);
        AdminUser admin = find(username);

        if (!passwordEncoder.matches(request.currentPassword(),
                admin.getPasswordHash())) {

            attempts.recordFailure(username, ip);
            AuditLog.warn("password change refused: wrong current password", username, ip);
            audit.recordAuth(AuditAction.PASSWORD_CHANGE_FAILED, username, ip, userAgent);
            throw new BusinessRuleException("Current password is incorrect");
        }
        PasswordPolicy.validate(request.newPassword(), username, request.currentPassword());

        attempts.recordSuccess(username, ip);
        admin.changePassword(passwordEncoder.encode(request.newPassword()));
        adminUsers.saveAndFlush(admin);
        refreshTokens.revokeAll(admin, "PASSWORD_CHANGED");
        AuditLog.info("password changed", username, ip);
        audit.recordAuth(AuditAction.PASSWORD_CHANGED,
                username, ip, userAgent);

        return respond(refreshTokens.startSession(admin, ip, userAgent));
    }

    private AdminUser find(String username) {

        return adminUsers.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user", username));
    }

    private AdminLoginResponse respond(RefreshTokenService.Issued refresh) {

        return new AdminLoginResponse(
                jwtService.issueToken(refresh.username()),
                "Bearer",
                jwtProperties.getExpirationMinutes(),
                refresh.rawToken(),
                refresh.expiresAt(),
                refresh.username(),
                refresh.fullName(),
                refresh.passwordChangeRequired());
    }
}
