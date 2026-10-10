package com.ironoak.dto.response;

import com.ironoak.domain.ServiceQuote;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.domain.enums.ServiceQuoteStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A quote request. Once QUOTED, orderNumber is what the customer pays with
 * (GET /api/orders/{orderNumber}, POST /api/orders/{orderNumber}/checkout-session) and orderStatus
 * and grandTotal (price + tax) follow that order. Reads lazy relations: map inside a transaction.
 */
public record ServiceQuoteResponse(
        Long id,
        ServiceQuoteStatus status,
        String serviceCode,
        String serviceName,
        String customerName,
        String customerEmail,
        String customerPhone,
        String country,
        String city,
        String description,
        BigDecimal quotedPrice,
        String staffNote,
        Long orderId,
        String orderNumber,
        OrderStatus orderStatus,
        BigDecimal grandTotal,
        OffsetDateTime quotedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ServiceQuoteResponse from(ServiceQuote q) {
        var order = q.getOrder();
        return new ServiceQuoteResponse(q.getId(), q.getStatus(), q.getServiceOffering().getCode(),
                q.getServiceOffering().getName(), q.getCustomerName(), q.getCustomerEmail(),
                q.getCustomerPhone(), q.getCountry(), q.getCity(), q.getDescription(), q.getQuotedPrice(),
                q.getStaffNote(),
                order == null ? null : order.getId(),
                order == null ? null : order.getOrderNumber(),
                order == null ? null : order.getStatus(),
                order == null ? null : order.getGrandTotal(),
                q.getQuotedAt(), q.getCreatedAt(), q.getUpdatedAt());
    }
}
