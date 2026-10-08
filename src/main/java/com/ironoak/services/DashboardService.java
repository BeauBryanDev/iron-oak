package com.ironoak.services;

import com.ironoak.domain.enums.ComplaintStatus;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.dto.response.DashboardKPIResponse;
import com.ironoak.repository.ComplaintRepository;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.OrderItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final int TOP_PRODUCTS = 5;

    private final CustomerOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ComplaintRepository complaints;

    public DashboardService(CustomerOrderRepository orders,
                            OrderItemRepository orderItems,
                            ComplaintRepository complaints) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.complaints = complaints;
    }

    public DashboardKPIResponse kpis() {
        var top = orderItems.findTopSellingProducts(OrderStatus.COMPLETED, PageRequest.of(0, TOP_PRODUCTS))
                .stream()
                .map(t -> new DashboardKPIResponse.TopProduct(t.getProductId(), t.getName(), t.getUnitsSold()))
                .toList();
        return new DashboardKPIResponse(
                orders.countByStatus(OrderStatus.CONFIRMED) + orders.countByStatus(OrderStatus.IN_PROGRESS),
                orders.countByStatus(OrderStatus.COMPLETED),
                orders.countByStatus(OrderStatus.CANCELLED),
                orders.countByChannel(OrderChannel.AGENT_CHAT),
                orders.sumTotalByStatus(OrderStatus.COMPLETED),
                complaints.countByStatus(ComplaintStatus.PENDING),
                top);
    }
}
