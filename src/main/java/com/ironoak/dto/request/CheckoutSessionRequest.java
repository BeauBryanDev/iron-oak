package com.ironoak.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The email the order was placed under: it proves the caller may pay this order. */
public record CheckoutSessionRequest(@NotBlank @Email @Size(max = 254) String email) {
}
