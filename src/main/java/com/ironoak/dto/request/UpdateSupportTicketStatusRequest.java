package com.ironoak.dto.request;

import com.ironoak.domain.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSupportTicketStatusRequest(@NotNull TicketStatus status) {
}
