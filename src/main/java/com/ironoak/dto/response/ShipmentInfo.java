package com.ironoak.dto.response;

import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.enums.ShipmentStatus;
import com.ironoak.domain.enums.ShippingMode;
import com.ironoak.services.ShippingCalculator;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Where an order's parcel is, for the order pages and Piper. status is null until staff ship it.
 * estimatedDeliveryDays is the simple model in ShippingCalculator (handling + road or air days);
 * expectedDeliveryDate = shipped date + those days, once shipped. Null for orders with nothing
 * to ship.
 */
public record ShipmentInfo(
        ShipmentStatus status,
        String carrier,
        String trackingNumber,
        OffsetDateTime shippedAt,
        OffsetDateTime deliveredAt,
        Integer estimatedDeliveryDays,
        LocalDate expectedDeliveryDate) {

    public static ShipmentInfo from(CustomerOrder order) {
        if (order.getShippingMode() == null || order.getShippingMode() == ShippingMode.NONE) {
            return null;
        }
        Integer days = ShippingCalculator.deliveryDays(order.getShippingMode(), order.getShippingDistanceKm());
        LocalDate expected = order.getShippedAt() == null || days == null ? null
                : order.getShippedAt().toLocalDate().plusDays(days);
        return new ShipmentInfo(order.getShipmentStatus(), order.getCarrier(), order.getTrackingNumber(),
                order.getShippedAt(), order.getDeliveredAt(), days, expected);
    }
}
