package com.ironoak.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** What a cart needs to show shipping before checkout: the lines and where they are going. */
public record ShippingQuoteRequest(
        @Pattern(regexp = "[A-Za-z]{2}", message = "must be a two-letter ISO country code") String country,
        @Size(max = 100) String city,
        @Size(max = 100) String province,
        @NotEmpty @Valid List<CreateOrderRequest.Item> items) {
}
