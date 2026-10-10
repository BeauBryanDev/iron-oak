package com.ironoak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds app.stripe.*. Both values come from .env (STRIPE_API_KEY, STRIPE_WEBHOOK_SECRET) and
 * may be blank: the app still starts, and the payment endpoints answer 503 until they are set.
 */
@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

    /** A restricted key (rk_...) with Checkout Sessions write, PaymentIntents/Charges read and Refunds write. */
    private String apiKey = "";

    /** whsec_... of the event destination (or of `stripe listen` while developing). */
    private String webhookSecret = "";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
