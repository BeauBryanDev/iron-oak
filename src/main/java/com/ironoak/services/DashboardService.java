package com.ironoak.services;

import com.ironoak.domain.enums.BookingStatus;
import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.domain.enums.ComplaintStatus;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.domain.enums.TicketStatus;
import com.ironoak.dto.response.DashboardKPIResponse;
import com.ironoak.repository.ComplaintRepository;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.OrderItemRepository;
import com.ironoak.repository.ServiceBookingRepository;
import com.ironoak.repository.SupportTicketRepository;
import com.ironoak.repository.WarrantyClaimRepository;
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
    private final ServiceBookingRepository bookings;
    private final WarrantyClaimRepository claims;
    private final SupportTicketRepository tickets;

    public DashboardService(CustomerOrderRepository orders,
            OrderItemRepository orderItems,
            ComplaintRepository complaints,
            ServiceBookingRepository bookings,
            WarrantyClaimRepository claims,
            SupportTicketRepository tickets) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.complaints = complaints;
        this.bookings = bookings;
        this.claims = claims;
        this.tickets = tickets;
    }

    public DashboardKPIResponse kpis() {

        var top = orderItems.findTopSellingProducts(OrderStatus.COMPLETED,
                PageRequest.of(0, TOP_PRODUCTS))
                .stream()
                .map(t -> new DashboardKPIResponse.TopProduct(t.getProductId(),
                        t.getName(), t.getUnitsSold()))
                .toList();

        return new DashboardKPIResponse(
                orders.countByStatus(OrderStatus.CONFIRMED) + orders.countByStatus(OrderStatus.IN_PROGRESS),
                orders.countByStatus(OrderStatus.PENDING_PAYMENT),
                orders.countByStatus(OrderStatus.COMPLETED),
                orders.countByStatus(OrderStatus.CANCELLED),
                orders.countByStatus(OrderStatus.EXPIRED),
                orders.countByChannel(OrderChannel.WEB_CHECKOUT),
                orders.countByChannel(OrderChannel.PIPER),
                orders.sumTotalByStatus(OrderStatus.COMPLETED),
                complaints.countByStatus(ComplaintStatus.PENDING),
                bookings.countByStatus(BookingStatus.REQUESTED),
                claims.countByStatus(ClaimStatus.OPEN) + claims.countByStatus(ClaimStatus.IN_REVIEW),
                tickets.countByStatus(TicketStatus.OPEN) + tickets.countByStatus(TicketStatus.IN_PROGRESS),
                top);
    }
}
