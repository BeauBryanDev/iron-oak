package com.ironoak.dto.response;

import java.time.OffsetDateTime;

/** Send the customer's browser to checkoutUrl; the page expires at expiresAt. */
public record CheckoutSessionResponse(
        String orderNumber,
        Long paymentId,
        String checkoutUrl,
        OffsetDateTime expiresAt) {
}
