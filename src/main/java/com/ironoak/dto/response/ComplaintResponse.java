package com.ironoak.dto.response;

import com.ironoak.domain.enums.ComplaintStatus;

import java.time.OffsetDateTime;

public record ComplaintResponse(
        Long id,
        String customerName,
        OffsetDateTime complaintDatetime,
        String product,
        String description,
        ComplaintStatus status,
        OffsetDateTime createdAt) {
}
