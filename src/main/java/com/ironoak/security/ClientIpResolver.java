package com.ironoak.security;

import com.ironoak.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Works out which address a request came from, for rate limits and audit logs.
 *
 * By default only the socket address is used. X-Forwarded-For is client-controlled, so it is
 * honoured only when app.security.trust-forwarded-for is on (the app sits behind a proxy that
 * appends to it), and then only its LAST entry, which is the one our own proxy wrote. Taking
 * the first entry would let any caller pick their own address.
 */
@Component
public class ClientIpResolver {

    private final boolean trustForwardedFor;

    public ClientIpResolver(SecurityProperties properties) {
        this.trustForwardedFor = properties.isTrustForwardedFor();
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String last = forwarded.substring(forwarded.lastIndexOf(',') + 1).trim();
                if (!last.isEmpty() && last.length() <= 45) {
                    return last;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
