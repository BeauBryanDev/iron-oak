package com.ironoak.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public quote request for a QUOTE-priced service, named by its code (e.g. SPINDLE_TOOLING_SERVICE).
 * country + city must be inside the service area (ServiceArea); country decides the tax.
 */
public record CreateServiceQuoteRequest(
        @NotBlank @Size(max = 50) String serviceCode,
        @NotBlank @Size(max = 200) String customerName,
        @NotBlank @Email @Size(max = 200) String customerEmail,
        @Size(max = 30) String customerPhone,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "must be a two-letter ISO country code") String country,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 4000) String description) {
}
