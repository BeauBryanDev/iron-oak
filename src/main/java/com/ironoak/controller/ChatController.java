package com.ironoak.controller;

import com.ironoak.dto.request.ChatMessageRequest;
import com.ironoak.dto.response.ChatReplyResponse;
import com.ironoak.dto.response.ChatSessionResponse;
import com.ironoak.services.ChatOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public chat with Piper (SecurityConfig permits /api/chat/**, RateLimitFilter
 * limits it per IP). The session token travels in the X-Chat-Session header,
 * not in the URL, so it does not end up in proxy access logs.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    public static final String SESSION_HEADER = "X-Chat-Session";

    private final ChatOrchestrationService chat;

    public ChatController(ChatOrchestrationService chat) {
        this.chat = chat;
    }

    /** Starts a chat; keep the returned sessionToken to continue it. */
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse start() {

        return chat.startSession();
    }

    /** The conversation so far, e.g. to redraw the chat after a page reload. */
    @GetMapping("/messages")
    public ChatSessionResponse history(@RequestHeader(SESSION_HEADER) String sessionToken) {

        return chat.history(sessionToken);
    }

    /** One message to Piper; answers with the stored message and Piper's reply. */
    @PostMapping("/messages")
    public ChatReplyResponse send(@RequestHeader(SESSION_HEADER) String sessionToken,
            @Valid @RequestBody ChatMessageRequest request) {

        return chat.send(sessionToken, request.content(), request.image_id());
    }
}
