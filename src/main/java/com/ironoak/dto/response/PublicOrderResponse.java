package com.ironoak.dto.response;

import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.domain.enums.ShippingStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * What anyone holding an order number may see: no name, email, phone or
 * address. payable is true
 * when the order can be paid right now (unpaid, shipping quoted, stock hold
 * still valid).
 */
public record PublicOrderResponse(
        String orderNumber,
        OrderStatus status,
        ShippingStatus shippingStatus,
        boolean payable,
        String currency,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal taxes,
        BigDecimal grandTotal,
        OffsetDateTime reservationExpiresAt,
        OffsetDateTime createdAt,
        List<Line> items) {

    public record Line(String name,
            String itemCode,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal) {
    }
}
