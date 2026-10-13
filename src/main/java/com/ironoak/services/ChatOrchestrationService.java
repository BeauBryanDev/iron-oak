package com.ironoak.services;

import com.ironoak.agent.PiperAssistant;
import com.ironoak.agent.PiperAssistant.Turn;
import com.ironoak.domain.ChatMessage;
import com.ironoak.domain.ChatSession;
import com.ironoak.domain.enums.MessageRole;
import com.ironoak.dto.response.ChatMessageResponse;
import com.ironoak.dto.response.ChatReplyResponse;
import com.ironoak.dto.response.ChatSessionResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.ChatMessageRepository;
import com.ironoak.repository.ChatSessionRepository;
import com.ironoak.security.ChatSessionTokens;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Chats with Piper. A visitor starts a session and gets a signed session token
 * (ChatSessionTokens); each message then runs in three steps:
 *
 * 1. a short read: check the token and the limits, load the recent history;
 * 2. Piper's answer from the language model, outside any transaction, so a slow
 *    model never holds a database connection;
 * 3. a short write: the customer's message and Piper's answer are saved together.
 *
 * If the model fails, nothing is saved (503) and the customer can send the same
 * message again. Tool messages (role TOOL) are stored for the record but are
 * never shown to the customer or replayed to the model.
 */
@Service
public class ChatOrchestrationService {

    /** Earlier messages sent to the model with each new one (cost and context limit). */
    public static final int HISTORY_MESSAGES = 20;
    /** Customer messages allowed in one session; after that, start a new chat. */
    public static final int MAX_USER_MESSAGES_PER_SESSION = 100;

    private static final Set<MessageRole> VISIBLE = Set.of(MessageRole.USER,
            MessageRole.ASSISTANT);

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ChatSessionTokens tokens;
    private final PiperAssistant piper;
    private final TransactionOperations transactions;

    public ChatOrchestrationService(ChatSessionRepository sessions,
            ChatMessageRepository messages,
            ChatSessionTokens tokens,
            PiperAssistant piper,
            TransactionOperations transactions) {

        this.sessions = sessions;
        this.messages = messages;
        this.tokens = tokens;
        this.piper = piper;
        this.transactions = transactions;
    }

    /** A new anonymous chat. The token in the response is the only way back into it. */
    public ChatSessionResponse startSession() {

        ChatSession session = transactions.execute(status ->
                sessions.saveAndFlush(new ChatSession(null)));

        return new ChatSessionResponse(session.getId(),
                tokens.issue(session.getId()),
                session.getStartedAt(),
                List.of());
    }

    /** The conversation so far (customer and Piper messages, oldest first). */
    public ChatSessionResponse history(String sessionToken) {

        return transactions.execute(status -> {

            ChatSession session = requireSession(sessionToken);
            List<ChatMessageResponse> conversation = messages
                    .findByChatSessionIdAndRoleInOrderByIdAsc(session.getId(), VISIBLE)
                    .stream()
                    .map(ChatMessageResponse::from)
                    .toList();

            return new ChatSessionResponse(session.getId(),
                    sessionToken,
                    session.getStartedAt(),
                    conversation);
        });
    }

    /** Sends one customer message and returns it with Piper's answer. */
    public ChatReplyResponse send(String sessionToken, String content) {

        return send(sessionToken, content, null);
    }

    public ChatReplyResponse send(String sessionToken, String content, String imageId) {

        String text = content == null ? "" : content.strip();

        if (text.isEmpty()) {
            throw new BusinessRuleException("The message is empty");
        }

        // 1. read: session, limit, recent history
        Context context = transactions.execute(status -> {

            ChatSession session = requireSession(sessionToken);
            long sent = messages.countByChatSessionIdAndRole(session.getId(), MessageRole.USER);

            if (sent >= MAX_USER_MESSAGES_PER_SESSION) {
                throw new BusinessRuleException("This chat has reached "
                        + MAX_USER_MESSAGES_PER_SESSION + " messages; please start a new chat");
            }
            return new Context(session.getId(), recentTurns(session.getId()));
        });

        // 2. Piper answers (no transaction open)
        String modelText = imageId == null || imageId.isBlank()
                ? text
                : text + "\n\n[Attached temporary image reference: " + imageId
                        + ". Use identify_tool_from_image only if the customer asks about the tool in this image.]";
        String answer = piper.reply(context.history(), modelText);

        // 3. write: both messages, or neither
        return transactions.execute(status -> {

            ChatSession session = sessions.getReferenceById(context.sessionId());
            ChatMessage question = messages.save(new ChatMessage(session,
                    MessageRole.USER,
                    text,
                    null));
            ChatMessage reply = messages.save(new ChatMessage(session,
                    MessageRole.ASSISTANT,
                    answer,
                    null));
            messages.flush();

            return new ChatReplyResponse(context.sessionId(),
                    ChatMessageResponse.from(question),
                    ChatMessageResponse.from(reply));
        });
    }

    private record Context(Long sessionId, List<Turn> history) {
    }

    /** The last HISTORY_MESSAGES customer and Piper messages, oldest first. */
    private List<Turn> recentTurns(Long sessionId) {

        List<ChatMessage> newestFirst = messages.findByChatSessionIdAndRoleInOrderByIdDesc(
                sessionId,
                VISIBLE,
                PageRequest.of(0, HISTORY_MESSAGES));
        List<Turn> turns = new ArrayList<>(newestFirst.size());

        for (ChatMessage message : newestFirst) {
            turns.add(new Turn(message.getRole(), message.getContent()));
        }
        Collections.reverse(turns);

        return turns;
    }

    /** A bad, tampered or unknown token is a 404, like a missing session. */
    private ChatSession requireSession(String sessionToken) {

        return tokens.verify(sessionToken)
                .flatMap(sessions::findById)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session", "(token)"));
    }
}
