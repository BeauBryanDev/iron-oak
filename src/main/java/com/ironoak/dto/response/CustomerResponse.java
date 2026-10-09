package com.ironoak.dto.response;

import java.time.OffsetDateTime;

public record CustomerResponse(
        Long id,
        String name,
        String email,
        String phone,
        String address,
        OffsetDateTime createdAt) {
}
