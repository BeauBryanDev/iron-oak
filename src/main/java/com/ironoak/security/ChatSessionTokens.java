package com.ironoak.security;

import com.ironoak.config.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

/**
 * The key a browser holds for its chat with Piper: "{sessionId}.{signature}".
 *
 * chat_session.id is a plain counter and /api/chat/** is public, so the id
 * alone would let anyone read other visitors' chats by counting. The signature
 * (HMAC-SHA256 of the id with JWT_SECRET_KEY) proves the server issued it, with
 * no extra column. A tampered or guessed token reads as "not found".
 */
@Component
public class ChatSessionTokens {

    private static final String ALGORITHM = "HmacSHA256";
    // keeps these signatures distinct from anything else signed with the same
    // secret
    private static final String PURPOSE = "iron-oak-chat-session:";

    private final SecretKeySpec key;

    public ChatSessionTokens(JwtProperties properties) {

        this.key = new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8),
                ALGORITHM);
    }

    public String issue(long sessionId) {

        return sessionId + "." + sign(sessionId);
    }

    /** The session id inside a valid token, or empty for anything else. */
    public Optional<Long> verify(String token) {

        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');

        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        long sessionId;

        try {
            sessionId = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        byte[] expected = sign(sessionId).getBytes(StandardCharsets.US_ASCII);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);

        // constant time, so the signature cannot be guessed byte by byte
        return MessageDigest.isEqual(expected, given)
                ? Optional.of(sessionId)
                : Optional.empty();
    }

    private String sign(long sessionId) {

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((PURPOSE + sessionId).getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign chat session " + sessionId, e);
        }
    }
}
