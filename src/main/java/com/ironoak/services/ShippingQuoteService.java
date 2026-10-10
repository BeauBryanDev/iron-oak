package com.ironoak.services;

import com.ironoak.domain.enums.ShippingSource;
import com.ironoak.domain.MillingMachine;
import com.ironoak.domain.OrderItem;
import com.ironoak.domain.Product;
import com.ironoak.dto.request.CreateOrderRequest;
import com.ironoak.dto.request.ShippingQuoteRequest;
import com.ironoak.dto.response.ShippingQuoteResponse;
import com.ironoak.exceptions.InvalidOrderException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.MillingMachineRepository;
import com.ironoak.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Public shipping estimate for a cart; ShippingService also prices the real
 * order at checkout.
 */
@Service
@Transactional(readOnly = true)
public class ShippingQuoteService {

    private final ProductRepository products;
    private final MillingMachineRepository machines;
    private final ShippingService shipping;

    public ShippingQuoteService(ProductRepository products,
            MillingMachineRepository machines,
            ShippingService shipping) {
        this.products = products;
        this.machines = machines;
        this.shipping = shipping;
    }

    public ShippingQuoteResponse quote(ShippingQuoteRequest request) {

        List<OrderItem> lines = new ArrayList<>();
        for (CreateOrderRequest.Item line : request.items()) {

            switch (line.itemType()) {

                case PRODUCT -> {

                    Product product = products.findById(line.referenceId())
                            .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                            .orElseThrow(() -> new ResourceNotFoundException("Product", line.referenceId()));
                    lines.add(OrderItem.ofProduct(product, line.quantity()));
                }
                case MACHINE -> {

                    MillingMachine machine = machines.findById(line.referenceId())
                            .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                            .orElseThrow(() -> new ResourceNotFoundException("Milling machine",
                                    line.referenceId()));
                    lines.add(OrderItem.ofMachine(machine, line.quantity()));
                }
                case SERVICE -> {
                    // services are not shipped
                }
            }
        }
        boolean physical = !lines.isEmpty();
        if (physical && (request.country() == null || request.country().isBlank())) {

            throw new InvalidOrderException("country is required to quote shipping for products or machines");
        }
        // Browsing never spends a Google call: stored routes or the estimate only.
        ShippingService.Quote quote = shipping.quote(request.country(),
                request.city(),
                request.province(),
                lines, false);

        return new ShippingQuoteResponse("USD",
                quote.country(),
                quote.status(),
                quote.mode(),
                quote.domestic(),
                quote.distanceKm(),
                quote.source(),
                quote.source() == ShippingSource.ESTIMATE,
                quote.cost(),
                quote.note());
    }
}
