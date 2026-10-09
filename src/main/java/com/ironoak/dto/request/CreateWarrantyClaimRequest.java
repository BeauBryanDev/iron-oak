package com.ironoak.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** customerEmail must match the customer on the order; the service checks it. */
public record CreateWarrantyClaimRequest(
        @NotBlank @Email @Size(max = 200) String customerEmail,
        @NotNull Long orderId,
        @NotNull Long orderItemId,
        @NotBlank String description) {
}
