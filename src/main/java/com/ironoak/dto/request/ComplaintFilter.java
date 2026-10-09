package com.ironoak.dto.request;

import com.ironoak.domain.enums.ComplaintStatus;

import java.time.LocalDate;
import java.util.List;

/** Query parameters of GET /api/admin/complaints; from/to are inclusive UTC dates on the filing date. */
public record ComplaintFilter(
        List<ComplaintStatus> status,
        LocalDate from,
        LocalDate to,
        String q) {
}
