package com.ironoak.security;

import com.ironoak.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test: no Spring, no database. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long";

    private JwtProperties properties;
    private JwtService jwt;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        jwt = new JwtService(properties);
        jwt.initialiseKey();
    }

    @Test
    void issuedAccessTokensVerifyAndCarryTheSubjectAndIssueTime() {
        var parsed = jwt.parse(jwt.issueToken("owner"));
        assertThat(parsed).isPresent();
        assertThat(parsed.get().username()).isEqualTo("owner");
        assertThat(parsed.get().issuedAt()).isBeforeOrEqualTo(java.time.Instant.now());
    }

    @Test
    void tokensAreUniquePerIssue() {
        assertThat(jwt.issueToken("owner")).isNotEqualTo(jwt.issueToken("owner"));
    }

    @Test
    void rejectsTamperedWrongKeyExpiredAndNonAccessTokens() {
        String good = jwt.issueToken("owner");
        String tampered = good.substring(0, good.length() - 2) + (good.endsWith("AA") ? "BB" : "AA");
        assertThat(jwt.parse(tampered)).isEmpty();
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
        assertThat(jwt.parse("")).isEmpty();

        SecretKey other = Keys.hmacShaKeyFor("another-secret-that-is-also-32-bytes-long!".getBytes(StandardCharsets.UTF_8));
        String foreign = Jwts.builder().subject("owner").issuer("iron-oak").claim("token_use", "access")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(other).compact();
        assertThat(jwt.parse(foreign)).isEmpty();

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().subject("owner").issuer("iron-oak").claim("token_use", "access")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000)).signWith(key).compact();
        assertThat(jwt.parse(expired)).isEmpty();

        String wrongUse = Jwts.builder().subject("owner").issuer("iron-oak").claim("token_use", "refresh")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        assertThat(jwt.parse(wrongUse)).isEmpty();

        String wrongIssuer = Jwts.builder().subject("owner").issuer("someone-else").claim("token_use", "access")
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        assertThat(jwt.parse(wrongIssuer)).isEmpty();
    }

    @Test
    void refusesToStartWithAWeakSecret() {
        properties.setSecret("too-short");
        assertThatThrownBy(() -> new JwtService(properties).initialiseKey())
                .isInstanceOf(IllegalStateException.class);
    }
}
