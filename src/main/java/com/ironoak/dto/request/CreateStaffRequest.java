package com.ironoak.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new admin account. temporaryPassword follows PasswordPolicy and must be replaced by the
 * person at first login; hand it over out of band.
 */
public record CreateStaffRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Email @Size(max = 200) String email,
        @Size(max = 200) String fullName,
        @NotBlank @Size(max = 200) String temporaryPassword) {
}
