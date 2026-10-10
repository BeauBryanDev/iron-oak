package com.ironoak.repository;

import com.ironoak.domain.PaymentWebhookEvent;
import com.ironoak.domain.enums.WebhookProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent> findByProviderEventId(String providerEventId);

    /** Deliveries that failed or never finished, oldest first, for a retry job. */
    List<PaymentWebhookEvent> findByProcessingStatusInOrderByReceivedAtAsc(
            java.util.Collection<WebhookProcessingStatus> statuses);
}
