package com.ironoak.security;

import com.ironoak.exceptions.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test: no Spring, no database. */
class PasswordPolicyTest {

    @Test
    void acceptsALongPassphrase() {
        assertThatCode(() -> PasswordPolicy.validate("correct horse battery staple", "owner", "old-password"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsShortBlankReusedAndUsernameBasedPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short", "owner", "x"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("            ", "owner", "x"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("same-password-123", "owner", "same-password-123"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("my-OWNER-password", "owner", "x"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsMoreThanBcryptsSeventyTwoBytes() {
        assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(73), "owner", "x"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(72), "owner", "x")).doesNotThrowAnyException();
        // 40 two-byte characters are 80 bytes even though they are only 40 characters
        assertThatThrownBy(() -> PasswordPolicy.validate("é".repeat(40), "owner", "x"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
