package com.ironoak.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Staff confirm a technician visit after checking a technician is available on site.
 * estimatedHours is required for HOURLY services (inside the service's min/max) and price for
 * QUOTE services; FIXED services use their listed price and take neither.
 */
public record ConfirmBookingRequest(
        @Positive @Digits(integer = 3, fraction = 1) BigDecimal estimatedHours,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal price) {
}
