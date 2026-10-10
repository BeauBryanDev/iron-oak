package com.ironoak.dto.response;

import com.ironoak.domain.enums.ShippingMode;
import com.ironoak.domain.enums.ShippingSource;
import com.ironoak.domain.enums.ShippingStatus;

import java.math.BigDecimal;

/**
 * USD. status ON_REQUEST means staff will quote the shipping (total is 0 until then); estimated
 * means the distance is a straight-line estimate that is confirmed when the order is placed.
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
        String note) {
}
