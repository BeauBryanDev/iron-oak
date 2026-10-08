package com.ironoak.dto.response;

import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        OrderChannel channel,
        String customerName,
        BigDecimal totalAmount,
        OffsetDateTime createdAt,
        List<OrderItemResponse> items) {
}
