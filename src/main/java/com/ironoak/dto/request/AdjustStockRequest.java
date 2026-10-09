package com.ironoak.dto.request;

import jakarta.validation.constraints.NotNull;

/** Positive adds stock (restock), negative removes it (damage, count correction). */
public record AdjustStockRequest(@NotNull Integer delta) {
}
