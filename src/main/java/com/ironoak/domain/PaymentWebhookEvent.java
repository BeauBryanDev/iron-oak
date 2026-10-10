package com.ironoak.domain;

import com.ironoak.domain.enums.WebhookProcessingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;

/**
 * Ledger of provider webhook deliveries. provider_event_id is unique, so a
 * redelivered event
 * is recognised and handled at most once; attempts and lastError support
 * retrying failures.
 */
@Entity
@Table(name = "payment_webhook_event")
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_event_id", nullable = false, unique = true, length = 255)
    @NotBlank
    @Size(max = 255)
    private String providerEventId;

    @Column(nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String provider = "stripe";

    @Column(name = "event_type", nullable = false, length = 150)
    @NotBlank
    @Size(max = 150)
    private String eventType;

    /**
     * Id of the provider object the event is about (checkout session, payment
     * intent, ...).
     */
    @Column(name = "object_id", length = 255)
    @Size(max = 255)
    private String objectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    @NotNull
    private WebhookProcessingStatus processingStatus = WebhookProcessingStatus.RECEIVED;

    @Column(nullable = false)
    @Min(0)
    private int attempts = 0;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    protected PaymentWebhookEvent() {
    }

    public PaymentWebhookEvent(String providerEventId,
            String provider,
            String eventType,
            String objectId) {
        this.providerEventId = providerEventId;
        this.provider = provider;
        this.eventType = eventType;
        this.objectId = objectId;
        this.receivedAt = OffsetDateTime.now();
    }

    public void markProcessing() {
        this.processingStatus = WebhookProcessingStatus.PROCESSING;
        this.attempts++;
    }

    public void markProcessed() {
        this.processingStatus = WebhookProcessingStatus.PROCESSED;
        this.processedAt = OffsetDateTime.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.processingStatus = WebhookProcessingStatus.FAILED;
        this.lastError = error;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public WebhookProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(WebhookProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(OffsetDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
