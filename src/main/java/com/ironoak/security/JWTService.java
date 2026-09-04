package com.ironoak.security;

import com.ironoak.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Issues and verifies the HS256 tokens used for admin dashboard access. */
@Service
public class JWTService {

    private final JwtProperties properties;
    private SecretKey signingKey;

    public JWTService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialiseKey() {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256 - check JWT_SECRET_KEY in .env");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(String username) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getExpirationMinutes() * 60);
        return Jwts.builder()
                .subject(username)
                .issuer("iron-oak")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the subject if the token is well-formed, correctly signed and unexpired,
     * otherwise empty. Never throws - an invalid token is an authentication outcome,
     * not an exceptional condition.
     */
    public Optional<String> extractUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer("iron-oak")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationMinutes() {
        return properties.getExpirationMinutes();
    }
}
