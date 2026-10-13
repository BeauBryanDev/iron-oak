package com.ironoak.dto.response;

/** What one exchange stored: the customer's message and Piper's answer. */
public record ChatReplyResponse(
        Long sessionId,
        ChatMessageResponse message,
        ChatMessageResponse reply) {
}
