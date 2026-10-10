package com.ironoak.dto.request;

import com.ironoak.domain.enums.OrderItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Guests need no account, but web and Piper orders must carry a name and email (the email is how
 * the customer reads the order back) and, when physical goods are ordered, a shipping address
 * (country, city, shippingAddress). Staff-entered orders may omit all of it.
 */
public record CreateOrderRequest(
        @Size(max = 200) String customerName,
        @Email @Size(max = 200) String customerEmail,
        @Size(max = 30) String customerPhone,
        @Pattern(regexp = "[A-Za-z]{2}", message = "must be a two-letter ISO country code") String country,
        @Size(max = 100) String province,
        @Size(max = 100) String city,
        @Size(max = 500) String shippingAddress,
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
