package com.ironoak.services;

import com.ironoak.config.StripeProperties;
import com.ironoak.exceptions.PaymentProviderException;
import com.ironoak.exceptions.WebhookSignatureException;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * The only class that talks to Stripe's SDK, so the rest of the code can be
 * tested without
 * the network. Every request carries an idempotency key; failures become
 * PaymentProviderException
 * (the Stripe message is logged, never returned to the caller).
 */
@Component
public class StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);

    private final StripeProperties properties;
    private volatile StripeClient client;

    public StripeGateway(StripeProperties properties) {
        this.properties = properties;
    }

    public Session createCheckoutSession(SessionCreateParams params,
            String idempotencyKey) {

        try {
            return client().checkout().sessions().create(params, options(idempotencyKey));

        } catch (StripeException e) {
            throw failed("create checkout session", e);
        }
    }

    public Session retrieveSession(String sessionId) {

        try {
            return client().checkout().sessions().retrieve(sessionId);

        } catch (StripeException e) {
            throw failed("retrieve checkout session", e);
        }
    }

    public Refund createRefund(RefundCreateParams params, String idempotencyKey) {
        try {
            return client().refunds().create(params, options(idempotencyKey));

        } catch (StripeException e) {
            throw failed("create refund", e);
        }
    }

    /**
     * Verifies the Stripe-Signature header over the exact raw body (5 minute
     * tolerance) and parses the event.
     */
    public Event verifyWebhook(byte[] payload, String signatureHeader) {
        String secret = properties.getWebhookSecret();

        if (secret == null || secret.isBlank()) {

            throw PaymentProviderException.notConfigured("STRIPE_WEBHOOK_SECRET");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new WebhookSignatureException();
        }
        try {
            return Webhook.constructEvent(new String(payload, StandardCharsets.UTF_8),
                    signatureHeader, secret);

        } catch (SignatureVerificationException | com.google.gson.JsonSyntaxException e) {
            throw new WebhookSignatureException();
        }
    }

    private StripeClient client() {
        StripeClient current = client;
        if (current == null) {
            String key = properties.getApiKey();
            if (key == null || key.isBlank()) {
                throw PaymentProviderException.notConfigured("STRIPE_API_KEY");
            }
            synchronized (this) {
                if (client == null) {
                    client = new StripeClient(key);
                }
                current = client;
            }
        }
        return current;
    }

    private static RequestOptions options(String idempotencyKey) {

        return RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
    }

    private static PaymentProviderException failed(String action, StripeException e) {

        log.error("Stripe could not {}: status={} code={} requestId={} message={}", action,
                e.getStatusCode(), e.getCode(), e.getRequestId(), e.getMessage());

        return PaymentProviderException.failed("Stripe could not " + action, e);
    }
}
