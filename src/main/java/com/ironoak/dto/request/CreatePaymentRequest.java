package com.ironoak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The amount is not sent: it is taken from the order total on the server. */
public record CreatePaymentRequest(
        @NotNull Long orderId,
        @NotBlank @Size(max = 50) String provider,
        @Size(max = 100) String providerReference) {
}
