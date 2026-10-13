package com.ironoak.exceptions;

/** Piper's language model could not answer (503); the customer can send the message again. */
public class AssistantUnavailableException extends RuntimeException {

    public AssistantUnavailableException() {
        super("Piper is not available right now. Please try again in a moment.");
    }
}
