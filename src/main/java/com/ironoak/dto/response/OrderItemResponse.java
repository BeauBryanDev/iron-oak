package com.ironoak.dto.response;

import com.ironoak.domain.enums.OrderItemType;

import java.math.BigDecimal;

/** referenceId is the product, service offering or milling machine id, per itemType. */
public record OrderItemResponse(
        Long id,
        OrderItemType itemType,
        Long referenceId,
        String name,
        int quantity,
        BigDecimal estimatedHours,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
