package com.ironoak.domain.enums;

/** Shipping is priced (QUOTED) or waits for staff to quote it (ON_REQUEST). Stored as VARCHAR (with a CHECK), not a PostgreSQL enum. */
public enum ShippingStatus {
    QUOTED,
    ON_REQUEST
}
