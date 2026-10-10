package com.ironoak.exceptions;

/** A webhook request whose signature does not verify. It is rejected without being processed. */
public class WebhookSignatureException extends RuntimeException {

    public WebhookSignatureException() {
        super("Invalid webhook signature");
    }
}
