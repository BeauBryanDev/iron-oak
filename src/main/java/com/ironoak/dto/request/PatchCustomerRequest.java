package com.ironoak.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Partial update (PATCH): a field left out (null) is unchanged. For email, phone and address
 * an empty string clears the value. name, when sent, must not be blank.
 */
public record PatchCustomerRequest(
        @Size(max = 200) String name,
        @Email @Size(max = 200) String email,
        @Size(max = 50) String phone,
        String address) {
}
