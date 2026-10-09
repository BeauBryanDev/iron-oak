package com.ironoak.dto.request;

import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.domain.enums.OrderStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Query parameters of GET /api/admin/orders; all optional. status may repeat; itemType keeps
 * orders with at least one such line; from/to are inclusive UTC dates on the order date; q
 * matches an order id exactly or the customer's name or email.
 */
public record OrderFilter(
        List<OrderStatus> status,
        OrderChannel channel,
        OrderItemType itemType,
        LocalDate from,
        LocalDate to,
        String q) {
}
