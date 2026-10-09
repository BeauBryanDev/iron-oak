package com.ironoak.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record RescheduleBookingRequest(@NotNull @Future OffsetDateTime scheduledAt) {
}
