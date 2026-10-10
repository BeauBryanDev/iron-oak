package com.ironoak.dto.request;

import com.ironoak.domain.enums.ServiceQuoteStatus;

import java.time.LocalDate;
import java.util.List;

/** Query parameters of GET /api/admin/service-quotes; q matches the customer's name or email. */
public record ServiceQuoteFilter(
        List<ServiceQuoteStatus> status,
        LocalDate from,
        LocalDate to,
        String q) {
}
