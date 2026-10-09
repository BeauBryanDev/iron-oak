package com.ironoak.dto.request;

import com.ironoak.domain.enums.ClaimStatus;

import java.time.LocalDate;
import java.util.List;

/** Query parameters of GET /api/admin/warranty-claims; q matches an order id, the customer, or the description. */
public record WarrantyClaimFilter(
        List<ClaimStatus> status,
        LocalDate from,
        LocalDate to,
        String q) {
}
