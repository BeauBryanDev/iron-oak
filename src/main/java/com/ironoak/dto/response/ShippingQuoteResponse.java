package com.ironoak.dto.response;

import com.ironoak.domain.enums.ShippingMode;
import com.ironoak.domain.enums.ShippingSource;
import com.ironoak.domain.enums.ShippingStatus;

import java.math.BigDecimal;

/**
 * USD. total is the shipping cost: status ON_REQUEST means staff will quote it (0 until then);
 * estimated means the distance is a straight-line estimate that is confirmed when the order is
 * placed. itemsSubtotal, taxRate and taxes use the same TaxService as real orders (items only,
 * only above 100.00); estimatedTotal = itemsSubtotal + total + taxes, i.e. what the order will
 * cost (without shipping while it is ON_REQUEST). Products and machines only: services are not
 * in the cart.
 */
public record ShippingQuoteResponse(
        String currency,
        String country,
        ShippingStatus status,
        ShippingMode mode,
        boolean domestic,
        BigDecimal distanceKm,
        ShippingSource source,
        boolean estimated,
        BigDecimal total,
        String note,
        BigDecimal itemsSubtotal,
        BigDecimal taxRate,
        BigDecimal taxes,
        BigDecimal estimatedTotal) {
}
