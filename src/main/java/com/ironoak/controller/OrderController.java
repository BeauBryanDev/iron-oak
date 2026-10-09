package com.ironoak.controller;

import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.dto.request.CreateOrderRequest;
import com.ironoak.dto.request.UpdateOrderStatusRequest;
import com.ironoak.dto.response.OrderResponse;
import com.ironoak.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guest checkout is public (POST /api/orders); everything else is under /api/admin and
 * requires a staff token. SecurityConfig owns that split.
 */
@RestController
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@Valid @RequestBody CreateOrderRequest request,
                                  @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return orders.create(request, OrderChannel.AGENT_CHAT, idempotencyKey);
    }

    /** A customer reads back their order by proving the email it was placed under. */
    @GetMapping("/api/orders/{id}")
    public OrderResponse getMine(@PathVariable Long id, @RequestParam String email) {
        return orders.getForCustomer(id, email);
    }

    @PostMapping("/api/admin/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createManual(@Valid @RequestBody CreateOrderRequest request,
                                      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return orders.create(request, OrderChannel.ADMIN_MANUAL, idempotencyKey);
    }

    @GetMapping("/api/admin/orders")
    public PagedModel<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        return new PagedModel<>(orders.list(status, pageable));
    }

    @GetMapping("/api/admin/orders/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orders.get(id);
    }

    @PatchMapping("/api/admin/orders/{id}/status")
    public OrderResponse updateStatus(@PathVariable Long id,
                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orders.updateStatus(id, request.status());
    }
}
