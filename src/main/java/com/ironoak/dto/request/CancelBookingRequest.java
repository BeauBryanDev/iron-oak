package com.ironoak.dto.request;

import jakarta.validation.constraints.Size;

/** The reason is optional; a cancelled booking keeps its row either way. */
public record CancelBookingRequest(@Size(max = 1000) String reason) {
}
