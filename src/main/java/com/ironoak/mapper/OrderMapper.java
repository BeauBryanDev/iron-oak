package com.ironoak.mapper;

import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.OrderItem;
import com.ironoak.dto.response.OrderItemResponse;
import com.ironoak.dto.response.OrderResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads lazy associations (items, customer, referenced
 * product/service/machine); call inside a transaction.
 */
@Component
public class OrderMapper {

    public OrderResponse toResponse(CustomerOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getChannel(),
                order.getCustomer() == null ? null : order.getCustomer().getName(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getItems().stream().map(this::toItemResponse).toList());
    }

    public List<OrderResponse> toResponses(List<CustomerOrder> orders) {
        return orders.stream().map(this::toResponse).toList();
    }

    public OrderItemResponse toItemResponse(OrderItem item) {
        Long referenceId;
        String name;
        switch (item.getItemType()) {
            case PRODUCT -> {
                referenceId = item.getProduct().getId();
                name = item.getProduct().getName();
            }
            case SERVICE -> {
                referenceId = item.getServiceOffering().getId();
                name = item.getServiceOffering().getName();
            }
            case MACHINE -> {
                referenceId = item.getMillingMachine().getId();
                name = item.getMillingMachine().getName();
            }
            default -> throw new IllegalStateException("Unhandled item type " + item.getItemType());
        }
        return new OrderItemResponse(
                item.getId(),
                item.getItemType(),
                referenceId,
                name,
                item.getQuantity(),
                item.getEstimatedHours(),
                item.getUnitPrice(),
                item.getSubtotal());
    }
}
