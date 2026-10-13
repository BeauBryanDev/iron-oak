package com.ironoak.services;

import com.ironoak.agent.PiperAssistant;
import com.ironoak.agent.PiperAssistant.Turn;
import com.ironoak.config.JwtProperties;
import com.ironoak.domain.ChatMessage;
import com.ironoak.domain.ChatSession;
import com.ironoak.domain.enums.MessageRole;
import com.ironoak.dto.response.ChatReplyResponse;
import com.ironoak.exceptions.AssistantUnavailableException;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.ChatMessageRepository;
import com.ironoak.repository.ChatSessionRepository;
import com.ironoak.security.ChatSessionTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit test: no Spring context, no database, no OpenAI. */
class ChatOrchestrationServiceTest {

    private static final long SESSION_ID = 42L;

    private ChatSessionRepository sessions;
    private ChatMessageRepository messages;
    private PiperAssistant piper;
    private ChatSessionTokens tokens;
    private ChatOrchestrationService chat;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret("test-secret-that-is-at-least-32-bytes-long");
        tokens = new ChatSessionTokens(jwt);

        sessions = mock(ChatSessionRepository.class);
        messages = mock(ChatMessageRepository.class);
        piper = mock(PiperAssistant.class);
        chat = new ChatOrchestrationService(sessions,
                messages,
                tokens,
                piper,
                TransactionOperations.withoutTransaction());

        session = new ChatSession(null);
        session.setId(SESSION_ID);
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessions.getReferenceById(SESSION_ID)).thenReturn(session);
        when(messages.save(any(ChatMessage.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void sessionTokensAreSignedAndCannotBeForged() {
        String token = tokens.issue(SESSION_ID);

        assertThat(tokens.verify(token)).contains(SESSION_ID);
        // same signature on another id, a changed signature, junk
        assertThat(tokens.verify("43" + token.substring(2))).isEmpty();
        assertThat(tokens.verify(token + "x")).isEmpty();
        assertThat(tokens.verify("42")).isEmpty();
        assertThat(tokens.verify(null)).isEmpty();

        assertThatThrownBy(() -> chat.send("43" + token.substring(2), "hola"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void sendsTheHistoryOldestFirstAndStoresBothMessages() {
        // the repository returns the newest first
        when(messages.findByChatSessionIdAndRoleInOrderByIdDesc(eq(SESSION_ID),
                anyCollection(),
                any(Pageable.class)))
                .thenReturn(List.of(
                        new ChatMessage(session, MessageRole.ASSISTANT, "Hola, soy Piper", null),
                        new ChatMessage(session, MessageRole.USER, "Hola", null)));
        when(piper.reply(anyList(), eq("Do you sell end mills?")))
                .thenReturn("Yes, six solid carbide end mills.");

        ChatReplyResponse result = chat.send(tokens.issue(SESSION_ID), "  Do you sell end mills?  ");

        verify(piper).reply(List.of(new Turn(MessageRole.USER, "Hola"),
                        new Turn(MessageRole.ASSISTANT, "Hola, soy Piper")),
                "Do you sell end mills?");
        assertThat(result.sessionId()).isEqualTo(SESSION_ID);
        assertThat(result.message().role()).isEqualTo(MessageRole.USER);
        assertThat(result.message().content()).isEqualTo("Do you sell end mills?");
        assertThat(result.reply().role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(result.reply().content()).isEqualTo("Yes, six solid carbide end mills.");
    }

    @Test
    void savesNothingWhenPiperCannotAnswer() {
        when(piper.reply(anyList(), anyString())).thenThrow(new AssistantUnavailableException());

        assertThatThrownBy(() -> chat.send(tokens.issue(SESSION_ID), "hello"))
                .isInstanceOf(AssistantUnavailableException.class);
        verify(messages, never()).save(any(ChatMessage.class));
    }

    @Test
    void refusesMessagesPastTheSessionLimitWithoutCallingPiper() {
        when(messages.countByChatSessionIdAndRole(anyLong(), eq(MessageRole.USER)))
                .thenReturn((long) ChatOrchestrationService.MAX_USER_MESSAGES_PER_SESSION);

        assertThatThrownBy(() -> chat.send(tokens.issue(SESSION_ID), "one more"))
                .isInstanceOf(BusinessRuleException.class);
        verify(piper, never()).reply(anyList(), anyString());
    }
}
