package com.ironoak.dto.response;

import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * grandTotal = subtotal + shippingCost + taxes. reservationExpiresAt is set while the order is
 * PENDING_PAYMENT: the stock is released if it is not paid by then.
 */
public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        OrderChannel channel,
        String customerName,
        String customerEmail,
        String phoneNumber,
        String country,
        String province,
        String city,
        String shippingAddress,
        String currency,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal taxes,
        BigDecimal grandTotal,
        OffsetDateTime reservationExpiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<OrderItemResponse> items) {
}
