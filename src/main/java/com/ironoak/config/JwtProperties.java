package com.ironoak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds app.jwt.* - the secret comes from JWT_SECRET_KEY in .env. */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HMAC signing key. Must be at least 32 bytes for HS256. */
    private String secret;

    private long expirationMinutes = 120;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
