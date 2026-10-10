package com.ironoak.dto.response;

import com.ironoak.domain.enums.OrderItemType;

import java.math.BigDecimal;

/**
 * name, itemCode and unitPrice are what the item was called and cost when it was bought.
 * referenceId is the product, service offering or milling machine id, per itemType.
 */
public record OrderItemResponse(
        Long id,
        OrderItemType itemType,
        Long referenceId,
        String name,
        String itemCode,
        int quantity,
        BigDecimal estimatedHours,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
