package com.ironoak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Length caps keep an oversized password from turning a login attempt into a CPU or memory cost. */
public record AdminLoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 128) String password) {
}
