package com.ironoak.security;

import com.ironoak.exceptions.BusinessRuleException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Rules for a new staff password. Length matters more than composition; the
 * upper bound is
 * BCrypt's: it silently ignores everything after 72 bytes, so a longer password
 * would give
 * a false sense of strength.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String newPassword,
            String username,
            String currentPassword) {

        if (newPassword.isBlank()) {
            throw new BusinessRuleException("New password must not be blank");
        }
        if (newPassword.codePointCount(0, newPassword.length()) < MIN_LENGTH) {
            throw new BusinessRuleException("New password must be at least " + MIN_LENGTH + " characters");
        }
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessRuleException("New password must be at most " + MAX_BYTES + " bytes");
        }
        if (newPassword.equals(currentPassword)) {
            throw new BusinessRuleException("New password must differ from the current one");
        }
        if (username != null && !username.isBlank()
                && newPassword.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))) {
            throw new BusinessRuleException("New password must not contain the username");
        }
    }
}
