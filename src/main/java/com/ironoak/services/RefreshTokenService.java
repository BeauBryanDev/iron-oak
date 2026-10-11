package com.ironoak.services;

import com.ironoak.config.JwtProperties;
import com.ironoak.domain.AdminRefreshToken;
import com.ironoak.domain.AdminUser;
import com.ironoak.repository.AdminRefreshTokenRepository;
import com.ironoak.security.AuditLog;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque refresh tokens with rotation and reuse detection.
 *
 * A token is 256 random bits, stored only as a SHA-256 hash (a database leak
 * yields nothing
 * usable). Every refresh revokes the presented token and issues a new one in
 * the same family.
 * Presenting an already-revoked token means it was copied, so the whole family
 * is revoked and
 * the real owner and the thief both have to log in again. A session also has a
 * hard maximum
 * age, however often it is refreshed.
 */
@Service
public class RefreshTokenService {

    /**
     * A freshly issued token: the raw value goes to the client once and is never
     * recoverable.
     */
    public record Issued(String rawToken,
            OffsetDateTime expiresAt,
            String username,
            String fullName,
            boolean passwordChangeRequired) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminRefreshTokenRepository tokens;
    private final JwtProperties properties;

    public RefreshTokenService(AdminRefreshTokenRepository tokens,
            JwtProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    /** Starts a new session family at login. */
    public Issued startSession(AdminUser admin,
            String ip,
            String userAgent) {

        OffsetDateTime now = OffsetDateTime.now();
        return issue(admin, UUID.randomUUID(), now, now, ip, userAgent);
    }

    /**
     * Exchanges a refresh token for a new one. Failures throw
     * BadCredentialsException (401);
     * noRollbackFor keeps the family revocation that reuse detection performs just
     * before it.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Issued rotate(String rawToken,
            String ip,
            String userAgent) {

        OffsetDateTime now = OffsetDateTime.now();

        AdminRefreshToken current = tokens.findForUpdateByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!current.getAdminUser().isActive()) {
            throw new BadCredentialsException("Invalid refresh token"); // disabled account
        }
        if (current.isRevoked()) {

            tokens.revokeFamily(current.getFamilyId(), now, "REUSE_DETECTED");
            AuditLog.warn("refresh token reuse, session revoked", current.getAdminUser().getUsername(), ip);

            throw new BadCredentialsException("Invalid refresh token");
        }
        OffsetDateTime sessionEnd = current.getSessionStartedAt().plusDays(properties.getRefreshMaxSessionDays());

        if (current.isExpired(now) || !sessionEnd.isAfter(now)) {

            throw new BadCredentialsException("Invalid refresh token");
        }
        if (tokens.revoke(current.getId(), now, "ROTATED") == 0) {
            // Lost a race with another use of the same token: treat it as reuse.
            tokens.revokeFamily(current.getFamilyId(), now, "REUSE_DETECTED");
            AuditLog.warn("refresh token used twice at once, session revoked",
                    current.getAdminUser().getUsername(), ip);

            throw new BadCredentialsException("Invalid refresh token");
        }
        return issue(current.getAdminUser(),
                current.getFamilyId(),
                current.getSessionStartedAt(),
                now, ip, userAgent);
    }

    /**
     * Ends the session the token belongs to. Unknown or already-revoked tokens are
     * ignored.
     */
    @Transactional
    public void revokeSession(String rawToken, String ip) {

        tokens.findByTokenHash(hash(rawToken)).ifPresent(token -> {

            tokens.revokeFamily(token.getFamilyId(),
                    OffsetDateTime.now(), "LOGOUT");

            AuditLog.info("logout", token.getAdminUser().getUsername(), ip);
        });
    }

    @Transactional
    public void revokeAll(AdminUser admin, String reason) {

        tokens.revokeAllForUser(admin.getId(), OffsetDateTime.now(), reason);
    }

    /** Housekeeping: dead rows are kept a day for forensics, then dropped. */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpired() {

        tokens.deleteExpiredBefore(OffsetDateTime.now().minusDays(1));
    }

    private Issued issue(AdminUser admin,
            UUID family,
            OffsetDateTime sessionStart,
            OffsetDateTime now,
            String ip, String userAgent) {

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        OffsetDateTime expires = now.plusDays(properties.getRefreshExpirationDays());

        OffsetDateTime sessionEnd = sessionStart.plusDays(properties.getRefreshMaxSessionDays());

        if (expires.isAfter(sessionEnd)) {

            expires = sessionEnd;
        }
        tokens.save(new AdminRefreshToken(admin,
                family, hash(raw),
                sessionStart, expires,
                truncate(userAgent, 255),
                truncate(ip, 45)));

        return new Issued(raw, expires, admin.getUsername(), admin.getFullName(), admin.isMustChangePassword());
    }

    static String hash(String rawToken) {

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static String truncate(String value, int max) {

        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
