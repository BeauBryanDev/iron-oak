package com.ironoak.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * openOrders are paid and being worked (CONFIRMED, IN_PROGRESS); pendingPaymentOrders are
 * waiting for payment and holding stock. Orders by channel count every status.
 */
public record DashboardKPIResponse(
        long openOrders,
        long pendingPaymentOrders,
        long completedOrders,
        long cancelledOrders,
        long expiredOrders,
        long webCheckoutOrders,
        long piperOrders,
        BigDecimal completedRevenue,
        long pendingComplaints,
        long requestedBookings,
        long openWarrantyClaims,
        long openSupportTickets,
        List<TopProduct> topProducts) {

    public record TopProduct(Long productId, String name, long unitsSold) {
    }
}
