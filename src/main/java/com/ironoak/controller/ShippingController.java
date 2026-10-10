package com.ironoak.controller;

import com.ironoak.dto.request.ShippingQuoteRequest;
import com.ironoak.dto.response.ShippingQuoteResponse;
import com.ironoak.services.ShippingQuoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public: the cart shows the shipping cost before the customer commits to an
 * order.
 */
@RestController
@RequestMapping("/api/shipping")
public class ShippingController {

    private final ShippingQuoteService quotes;

    public ShippingController(ShippingQuoteService quotes) {
        this.quotes = quotes;
    }

    @PostMapping("/quote")
    public ShippingQuoteResponse quote(@Valid @RequestBody ShippingQuoteRequest request) {

        return quotes.quote(request);
    }
}
