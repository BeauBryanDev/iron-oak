package com.ironoak.dto.response;

import java.time.OffsetDateTime;

/**
 * Returned by login, refresh and change-password. token is the short-lived
 * access JWT (send
 * it as a Bearer header); refreshToken is single-use and must be replaced by
 * the one in the
 * next response. Keep refreshToken out of localStorage if the admin app can
 * avoid it.
 */
public record AdminLoginResponse(
                String token,
                String tokenType,
                long expiresInMinutes,
                String refreshToken,
                OffsetDateTime refreshExpiresAt,
                String username,
                String fullName,
                boolean passwordChangeRequired) {
}
