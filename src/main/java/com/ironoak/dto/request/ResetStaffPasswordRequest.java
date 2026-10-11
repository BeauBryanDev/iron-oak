package com.ironoak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new temporary password for another admin; they must replace it at next login. */
public record ResetStaffPasswordRequest(@NotBlank @Size(max = 200) String temporaryPassword) {
}
