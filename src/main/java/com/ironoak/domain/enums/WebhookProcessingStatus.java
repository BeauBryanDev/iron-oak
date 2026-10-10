package com.ironoak.domain.enums;

/** Values allowed by chk_webhook_processing_status on {@code payment_webhook_event}. */
public enum WebhookProcessingStatus {
    RECEIVED,
    PROCESSING,
    PROCESSED,
    FAILED
}
