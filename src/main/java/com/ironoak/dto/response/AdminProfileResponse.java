package com.ironoak.dto.response;

import java.time.OffsetDateTime;

public record AdminProfileResponse(String username,
        String email,
        String fullName,
        OffsetDateTime lastLoginAt) {

}
