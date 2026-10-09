package com.ironoak.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Books a technician visit. The customer is found or created by email. */
public record CreateBookingRequest(
        @NotBlank @Size(max = 200) String customerName,
        @NotBlank @Email @Size(max = 200) String customerEmail,
        @Size(max = 50) String customerPhone,
        @NotNull Long serviceOfferingId,
        @NotBlank String locationAddress,
        @NotNull @Future OffsetDateTime scheduledAt,
        @Size(max = 100) String machineModel,
        String notes) {
}
