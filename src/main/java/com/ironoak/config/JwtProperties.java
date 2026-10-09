package com.ironoak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds app.jwt.* - the secret comes from JWT_SECRET_KEY in .env. */
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HMAC signing key. Must be at least 32 bytes for HS256. */
    private String secret;

    /** Access-token lifetime. Short on purpose: logout cannot recall an access token already issued. */
    private long expirationMinutes = 15;

    /** Refresh-token lifetime, renewed on every rotation. */
    private long refreshExpirationDays = 7;

    /** Hard cap on one login session, however often it is refreshed. */
    private long refreshMaxSessionDays = 30;

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

    public long getRefreshExpirationDays() {
        return refreshExpirationDays;
    }

    public void setRefreshExpirationDays(long refreshExpirationDays) {
        this.refreshExpirationDays = refreshExpirationDays;
    }

    public long getRefreshMaxSessionDays() {
        return refreshMaxSessionDays;
    }

    public void setRefreshMaxSessionDays(long refreshMaxSessionDays) {
        this.refreshMaxSessionDays = refreshMaxSessionDays;
    }
}
