package com.ironoak.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DashboardKPIResponse(
        long openOrders,
        long completedOrders,
        long cancelledOrders,
        long agentChatOrders,
        BigDecimal completedRevenue,
        long pendingComplaints,
        List<TopProduct> topProducts) {

    public record TopProduct(Long productId, String name, long unitsSold) {
    }
}
