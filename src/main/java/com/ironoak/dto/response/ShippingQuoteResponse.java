package com.ironoak.dto.response;

import java.math.BigDecimal;

/** USD. toolsShipping covers the products, machinesShipping the milling machines; services ship nothing. */
public record ShippingQuoteResponse(
        String currency,
        String country,
        boolean domestic,
        BigDecimal distanceKm,
        BigDecimal toolsShipping,
        BigDecimal machinesShipping,
        BigDecimal total) {
}
