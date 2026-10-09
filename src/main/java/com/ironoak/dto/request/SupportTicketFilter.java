package com.ironoak.dto.request;

import com.ironoak.domain.enums.TicketStatus;

import java.time.LocalDate;
import java.util.List;

/** Query parameters of GET /api/admin/support-tickets; q matches email, reason or summary. */
public record SupportTicketFilter(
        List<TicketStatus> status,
        LocalDate from,
        LocalDate to,
        String q) {
}
