package com.ironoak.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A chat with Piper. sessionToken is the key the browser keeps and sends back
 * in the X-Chat-Session header; sessionId can be given to a support ticket.
 */
public record ChatSessionResponse(
        Long sessionId,
        String sessionToken,
        OffsetDateTime startedAt,
        List<ChatMessageResponse> messages) {
}
