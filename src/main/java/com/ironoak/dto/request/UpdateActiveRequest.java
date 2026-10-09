package com.ironoak.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateActiveRequest(@NotNull Boolean isActive) {
}
