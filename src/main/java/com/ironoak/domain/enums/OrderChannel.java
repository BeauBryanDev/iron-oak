package com.ironoak.domain.enums;

/** Maps to the PostgreSQL enum type {@code order_channel}. */
public enum OrderChannel {
    /** The storefront checkout; the default. */
    WEB_CHECKOUT,
    /** Closed by the Piper assistant. */
    PIPER,
    /** Entered by staff. */
    ADMIN_MANUAL,
    /** Legacy value: V5 converted existing rows to WEB_CHECKOUT. Never written any more. */
    @Deprecated
    AGENT_CHAT
}
