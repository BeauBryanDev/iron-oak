package com.ironoak.dto.response;

public record AdminLoginResponse(
        String token,
        String username,
        String fullName,
        long expiresInMinutes) {
}
