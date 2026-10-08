package com.ironoak.dto.request;

import com.ironoak.domain.enums.OrderItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** The customer fields are optional: guests can order without leaving contact details. */
public record CreateOrderRequest(
        @Size(max = 200) String customerName,
        @Email @Size(max = 200) String customerEmail,
        @Size(max = 50) String customerPhone,
        @NotEmpty @Valid List<Item> items) {

    /**
     * referenceId is the product, service offering or milling machine id, per itemType.
     * estimatedHours is required for HOURLY services and ignored otherwise.
     */
    public record Item(
            @NotNull OrderItemType itemType,
            @NotNull Long referenceId,
            @Min(1) int quantity,
            BigDecimal estimatedHours) {
    }
}
