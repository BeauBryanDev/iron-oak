package com.ironoak.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * paidRevenue is every order paid so far (CONFIRMED, IN_PROGRESS, COMPLETED), counted from the
 * moment the customer pays; completedRevenue only the delivered or finished ones.
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
        BigDecimal paidRevenue,
        long pendingComplaints,
        long requestedBookings,
        long openWarrantyClaims,
        long openSupportTickets,
        List<TopProduct> topProducts) {

    public record TopProduct(Long productId, String name, long unitsSold) {
    }
}
