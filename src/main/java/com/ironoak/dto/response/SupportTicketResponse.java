package com.ironoak.dto.response;

import com.ironoak.domain.enums.TicketStatus;

import java.time.OffsetDateTime;

/** customerName is null for guests who gave only an email, or nothing at all. */
public record SupportTicketResponse(
        Long id,
        TicketStatus status,
        String customerName,
        String customerEmail,
        Long chatSessionId,
        String reason,
        String summary,
        OffsetDateTime createdAt) {
}
