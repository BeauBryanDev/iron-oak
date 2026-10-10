package com.ironoak.dto.request;

import jakarta.validation.constraints.Size;

/** The reason is kept in the audit log and on the Stripe refund. */
public record RefundPaymentRequest(@Size(max = 200) String reason) {
}
