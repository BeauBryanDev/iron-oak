package com.ironoak.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create (POST) and full replacement (PUT) of a customer. email, phone and address are
 * optional; on PUT, leaving one out clears it. The email is stored trimmed and lowercase.
 */
public record CustomerRequest(
        @NotBlank @Size(max = 200) String name,
        @Email @Size(max = 200) String email,
        @Size(max = 50) String phone,
        String address) {
}
