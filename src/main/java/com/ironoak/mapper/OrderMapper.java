package com.ironoak.mapper;

import java.time.OffsetDateTime;
import com.ironoak.domain.enums.ShippingStatus;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.dto.response.PublicOrderResponse;
import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.OrderItem;
import com.ironoak.dto.response.OrderItemResponse;
import com.ironoak.dto.response.OrderResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads lazy associations (items and the product/service/machine ids they point
 * to); call
 * inside a transaction. Names, codes and prices come from the order's own
 * snapshots.
 */
@Component
public class OrderMapper {

    public OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getChannel(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getPhoneNumber(),
                order.getCountry(),
                order.getProvince(),
                order.getCity(),
                order.getShippingAddress(),
                order.getCurrency(),
                order.getSubtotal(),
                order.getShippingCost(),
                order.getShippingStatus(),
                order.getShippingMode(),
                order.getShippingDistanceKm(),
                order.getShippingSource(),
                order.getTaxes(),
                order.getGrandTotal(),
                order.getReservationExpiresAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream().map(this::toItemResponse).toList());
    }

    /** The customer-safe view (no personal data); see PublicOrderResponse. */
    public PublicOrderResponse toPublicResponse(CustomerOrder order) {

        boolean payable = (order.getStatus() == OrderStatus.PENDING_PAYMENT
                || order.getStatus() == OrderStatus.PAYMENT_FAILED)
                && order.getShippingStatus() == ShippingStatus.QUOTED
                && (order.getReservationExpiresAt() == null
                        || order.getReservationExpiresAt().isAfter(OffsetDateTime.now()));

        return new PublicOrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getShippingStatus(),
                payable,
                order.getCurrency(),
                order.getSubtotal(),
                order.getShippingCost(),
                order.getTaxes(),
                order.getGrandTotal(),
                order.getReservationExpiresAt(),
                order.getCreatedAt(),
                order.getItems().stream().map(item -> new PublicOrderResponse.Line(item.getItemName(),
                        item.getItemCode(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal())).toList());
    }

    public List<OrderResponse> toResponses(List<CustomerOrder> orders) {

        return orders.stream().map(this::toResponse).toList();
    }

    public OrderItemResponse toItemResponse(OrderItem item) {

        Long referenceId = switch (item.getItemType()) {
            case PRODUCT -> item.getProduct().getId();
            case SERVICE -> item.getServiceOffering().getId();
            case MACHINE -> item.getMillingMachine().getId();
        };
        return new OrderItemResponse(

                item.getId(),
                item.getItemType(),
                referenceId,
                item.getItemName(),
                item.getItemCode(),
                item.getQuantity(),
                item.getEstimatedHours(),
                item.getUnitPrice(),
                item.getSubtotal());
    }
}
