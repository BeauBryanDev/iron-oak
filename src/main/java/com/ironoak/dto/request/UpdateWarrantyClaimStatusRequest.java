package com.ironoak.dto.request;

import com.ironoak.domain.enums.ClaimStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** resolutionNote is expected when moving to APPROVED, REJECTED or RESOLVED. */
public record UpdateWarrantyClaimStatusRequest(
        @NotNull ClaimStatus status,
        @Size(max = 2000) String resolutionNote) {
}
