package com.ironoak.dto.response;

import com.ironoak.domain.ChatMessage;
import com.ironoak.domain.enums.MessageRole;

import java.time.OffsetDateTime;

/** One message as the customer sees it (USER or ASSISTANT; tool calls stay internal). */
public record ChatMessageResponse(
        Long id,
        MessageRole role,
        String content,
        OffsetDateTime createdAt) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt());
    }
}
