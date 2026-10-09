package com.ironoak.dto.request;

import com.ironoak.domain.enums.PaymentStatus;

import java.time.LocalDate;
import java.util.List;

/** Query parameters of GET /api/admin/payments; q matches an order id or the provider reference. */
public record PaymentFilter(
        List<PaymentStatus> status,
        String provider,
        LocalDate from,
        LocalDate to,
        String q) {
}
