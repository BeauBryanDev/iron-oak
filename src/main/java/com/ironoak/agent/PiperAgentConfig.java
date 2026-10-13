package com.ironoak.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads Piper system prompt for the OpenAI chat model. SpringAIConfig
 * applies this prompt and registers Piper business tools with ChatClient.
 */
@Configuration
public class PiperAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(PiperAgentConfig.class);

    static final String SYSTEM_PROMPT_PATH = "Piper_Sys_Prompt.md";

    /** Used while Piper_Sys_Prompt.md is empty or missing. */
    static final String DEFAULT_SYSTEM_PROMPT = """
            You are Piper, the assistant of Iron & Oak, a hardware store and CNC machine
            supplier in Bogota, Colombia. You help customers with hand and power tools,
            end mills, milling machines and technician services, and with safe, practical
            advice on using and maintaining them.
            Answer in the customer's language (usually Spanish or English), briefly and
            clearly. All prices are in US dollars.
            Never invent prices, stock, order details or policies. If you do not have the
            information, say so and point the customer to the online catalog or to Iron &
            Oak staff.
            """;

    public String systemPrompt() {

        ClassPathResource resource = new ClassPathResource(SYSTEM_PROMPT_PATH);

        if (!resource.exists()) {
            log.warn("{} not found; Piper uses the built-in system prompt", SYSTEM_PROMPT_PATH);
            return DEFAULT_SYSTEM_PROMPT;
        }
        try (InputStream in = resource.getInputStream()) {

            String prompt = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();

            if (prompt.isEmpty()) {
                log.warn("{} is empty; Piper uses the built-in system prompt", SYSTEM_PROMPT_PATH);
                return DEFAULT_SYSTEM_PROMPT;
            }
            return prompt;

        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + SYSTEM_PROMPT_PATH, e);
        }
    }
}
