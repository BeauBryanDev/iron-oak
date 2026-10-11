package com.ironoak.domain.enums;

/**
 * customer_order.shipment_status (VARCHAR with a CHECK, V13); null until the order ships.
 * Moves forward only: SHIPPED -> IN_TRANSIT -> DELIVERED (SHIPPED -> DELIVERED is allowed too).
 */
public enum ShipmentStatus {
    SHIPPED,
    IN_TRANSIT,
    DELIVERED
}
