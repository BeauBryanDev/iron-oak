package com.ironoak.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Staff quote for an order's shipping, in the order's currency. */
public record SetShippingCostRequest(
        @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal shippingCost) {
}
