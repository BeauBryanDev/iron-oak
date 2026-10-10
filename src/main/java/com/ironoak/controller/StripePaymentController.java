package com.ironoak.controller;

import com.ironoak.dto.request.CheckoutSessionRequest;
import com.ironoak.dto.response.CheckoutSessionResponse;
import com.ironoak.exceptions.PaymentProviderException;
import com.ironoak.exceptions.WebhookSignatureException;
import com.ironoak.services.StripeCheckoutService;
import com.ironoak.services.StripeWebhookService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public Stripe endpoints. Starting a payment needs the order id plus the email
 * it was placed
 * under; the webhook needs a valid Stripe signature. Neither trusts anything
 * else in the request.
 */
@RestController
public class StripePaymentController {

    private final StripeCheckoutService checkout;
    private final StripeWebhookService webhook;

    public StripePaymentController(StripeCheckoutService checkout,
            StripeWebhookService webhook) {

        this.checkout = checkout;
        this.webhook = webhook;
    }

    /**
     * Returns the Stripe-hosted page to send the customer to; asking again returns
     * the same open page.
     */
    @PostMapping("/api/orders/{id:\\d+}/checkout-session")
    public CheckoutSessionResponse startCheckout(@PathVariable Long id,
            @Valid @RequestBody CheckoutSessionRequest request) {

        return checkout.createSession(id, request.email());
    }

    /**
     * Pays by order number alone (orders Piper created); no email needed, none
     * shown to Stripe.
     */
    @PostMapping("/api/orders/{orderNumber:[Ii][Oo]-[A-Za-z0-9-]+}/checkout-session")
    public CheckoutSessionResponse startCheckoutByNumber(@PathVariable String orderNumber) {

        return checkout.createSessionByNumber(orderNumber);
    }

    /**
     * The raw bytes are read untouched because the signature covers them exactly.
     * 200 means
     * "handled or already handled"; an error status makes Stripe retry.
     */
    @PostMapping("/api/payments/stripe/webhook")
    public ResponseEntity<Void> stripeWebhook(@RequestBody byte[] payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {

        try {
            webhook.handle(payload, signature);
        } catch (WebhookSignatureException | PaymentProviderException e) {
            throw e; // 400 / 503 / 502 through GlobalExceptionHandler

        } catch (RuntimeException e) {
            // already logged and recorded as FAILED in payment_webhook_event; 500 makes
            // Stripe retry
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok().build();
    }
}
