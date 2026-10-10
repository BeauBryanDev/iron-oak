package com.ironoak.domain.enums;

/**
 * service_quote.status (a VARCHAR with a CHECK, V10). REQUESTED -> QUOTED (an order was created
 * at the staff price) or DECLINED. Payment then follows the order's own status.
 */
public enum ServiceQuoteStatus {
    REQUESTED,
    QUOTED,
    DECLINED
}
