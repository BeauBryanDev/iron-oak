package com.ironoak.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports whether payments are configured, without calling Stripe. Missing keys
 * give UNKNOWN,
 * not DOWN: the shop still serves the catalog without them, so the instance
 * must stay in service.
 */
@Component("payments")
public class StripeHealthIndicator implements HealthIndicator {

    private final StripeProperties stripe;

    public StripeHealthIndicator(StripeProperties stripe) {
        this.stripe = stripe;
    }

    @Override
    public Health health() {

        boolean apiKey = stripe.getApiKey() != null && !stripe.getApiKey().isBlank();
        boolean webhook = stripe.getWebhookSecret() != null && !stripe.getWebhookSecret().isBlank();
        Health.Builder builder = apiKey && webhook ? Health.up() : Health.unknown();

        return builder
                .withDetail("apiKeyConfigured", apiKey)
                .withDetail("webhookSecretConfigured", webhook)
                .withDetail("mode", !apiKey ? "none" : stripe.getApiKey().contains("_live_") ? "live" : "test")
                .build();
    }
}
