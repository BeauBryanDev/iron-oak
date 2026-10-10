package com.ironoak.domain.enums;

/** Where a shipping price or distance came from. Stored as VARCHAR (with a CHECK), not a PostgreSQL enum. */
public enum ShippingSource {
    GOOGLE,
    MANUAL,
    ESTIMATE,
    FIXED,
    STAFF
}
