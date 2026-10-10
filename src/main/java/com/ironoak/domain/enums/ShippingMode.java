package com.ironoak.domain.enums;

/**
 * How an order travels; NONE when nothing ships. Stored as VARCHAR (with a
 * CHECK), not a PostgreSQL enum.
 */
public enum ShippingMode {
    NONE,
    SEA,
    ROAD,
    AIR
}
