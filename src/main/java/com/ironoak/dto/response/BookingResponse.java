package com.ironoak.dto.response;

import com.ironoak.domain.enums.BookingStatus;
import com.ironoak.domain.enums.OrderStatus;

import java.math.BigDecimal;

import java.time.OffsetDateTime;

/**
 * A technician visit. Once staff confirm it, orderNumber is what the customer pays with
 * (GET /api/orders/{orderNumber}, POST /api/orders/{orderNumber}/checkout-session); orderStatus
 * and grandTotal follow that order.
 */
public record BookingResponse(
        Long id,
        BookingStatus status,
        String serviceCode,
        String serviceName,
        String customerName,
        String locationAddress,
        String country,
        String city,
        OffsetDateTime scheduledAt,
        String machineModel,
        String notes,
        String cancelReason,
        Long orderId,
        String orderNumber,
        OrderStatus orderStatus,
        BigDecimal grandTotal,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
