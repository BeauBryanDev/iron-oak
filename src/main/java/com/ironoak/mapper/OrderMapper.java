package com.ironoak.mapper;

import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.OrderItem;
import com.ironoak.dto.response.OrderItemResponse;
import com.ironoak.dto.response.OrderResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads lazy associations (items and the product/service/machine ids they point to); call
 * inside a transaction. Names, codes and prices come from the order's own snapshots.
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
                order.getTaxes(),
                order.getGrandTotal(),
                order.getReservationExpiresAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream().map(this::toItemResponse).toList());
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
