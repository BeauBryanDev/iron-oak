package com.ironoak.agent;

import com.ironoak.domain.enums.MessageRole;
import com.ironoak.exceptions.AssistantUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The one call the chat makes to the language model: earlier turns plus the new
 * customer message in, Piper's answer out. Kept apart from the chat service so
 * the service can be tested without OpenAI.
 */
@Component
public class PiperAssistant {

    private static final Logger log = LoggerFactory.getLogger(PiperAssistant.class);

    private final ChatClient chatClient;

    /** One earlier message of the conversation (USER or ASSISTANT). */
    public record Turn(MessageRole role, String content) {
    }

    public PiperAssistant(ChatClient piperChatClient) {

        this.chatClient = piperChatClient;
    }

    /**
     * Piper's reply. Throws AssistantUnavailableException (503) when the model
     * cannot be reached or returns nothing.
     */
    public String reply(List<Turn> history, String userMessage) {

        List<Message> messages = history.stream()
                .map(PiperAssistant::toMessage)
                .toList();
        String answer;

        try {
            answer = chatClient.prompt()
                    .messages(messages)
                    .user(userMessage)
                    .call()
                    .content();

        } catch (RuntimeException e) {
            log.error("Piper could not get an answer from the chat model", e);
            throw new AssistantUnavailableException();
        }
        if (answer == null || answer.isBlank()) {
            log.error("The chat model returned an empty answer");
            throw new AssistantUnavailableException();
        }
        return answer.strip();
    }

    private static Message toMessage(Turn turn) {

        return turn.role() == MessageRole.ASSISTANT
                ? new AssistantMessage(turn.content())
                : new UserMessage(turn.content());
    }
}
