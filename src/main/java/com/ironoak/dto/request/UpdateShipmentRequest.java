package com.ironoak.dto.request;

import com.ironoak.domain.enums.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Staff update of an order's shipment. carrier is required when the order is first SHIPPED;
 * carrier and trackingNumber may be corrected until it is DELIVERED (null = unchanged).
 */
public record UpdateShipmentRequest(
        @NotNull ShipmentStatus status,
        @Size(max = 100) String carrier,
        @Size(max = 100) String trackingNumber) {
}
