package com.ironoak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Binds app.cors.* - the Next.js frontend origins allowed to call this API. */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:3000");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
