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
import java.util.UUID;

/** Issues and verifies the HS256 tokens used for admin dashboard access. */
@Service
public class JwtService {

    private final JwtProperties properties;
    private SecretKey signingKey;

    public JwtService(JwtProperties properties) {
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

    /** A verified access token: who it names and when it was issued. */
    public record AccessToken(String username, Instant issuedAt) {
    }

    private static final String TOKEN_USE = "token_use";
    private static final String ACCESS = "access";

    public String issueToken(String username) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getExpirationMinutes() * 60);
        return Jwts.builder()
                .subject(username)
                .issuer("iron-oak")
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_USE, ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the token's subject and issue time if it is well-formed, correctly signed with
     * HS256, unexpired and marked as an access token; otherwise empty. Never throws - an
     * invalid token is an authentication outcome, not an exceptional condition.
     */
    public Optional<AccessToken> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer("iron-oak")
                    .require(TOKEN_USE, ACCESS)
                    .clockSkewSeconds(30)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (claims.getSubject() == null || claims.getIssuedAt() == null) {
                return Optional.empty();
            }
            return Optional.of(new AccessToken(claims.getSubject(), claims.getIssuedAt().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<String> extractUsername(String token) {
        return parse(token).map(AccessToken::username);
    }

    public long getExpirationMinutes() {
        return properties.getExpirationMinutes();
    }
}
