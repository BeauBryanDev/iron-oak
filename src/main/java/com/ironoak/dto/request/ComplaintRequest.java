package com.ironoak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ComplaintRequest(
        @NotBlank @Size(max = 200) String customerName,
        @NotNull @PastOrPresent OffsetDateTime complaintDatetime,
        @NotBlank @Size(max = 200) String product,
        @NotBlank String description) {
}
