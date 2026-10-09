package com.ironoak.controller;

import com.ironoak.dto.request.CreatePaymentRequest;
import com.ironoak.dto.request.UpdatePaymentStatusRequest;
import com.ironoak.dto.response.PaymentResponse;
import com.ironoak.services.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Staff-only for now. A public checkout or provider webhook endpoint needs the provider's
 * signature verification, which belongs with the payment-provider integration.
 */
@RestController
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/api/admin/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {
        return payments.create(request);
    }

    @GetMapping("/api/admin/payments/{id}")
    public PaymentResponse get(@PathVariable Long id) {
        return payments.get(id);
    }

    @GetMapping("/api/admin/orders/{orderId}/payments")
    public List<PaymentResponse> forOrder(@PathVariable Long orderId) {
        return payments.listForOrder(orderId);
    }

    @PatchMapping("/api/admin/payments/{id}/status")
    public PaymentResponse updateStatus(@PathVariable Long id,
                                        @Valid @RequestBody UpdatePaymentStatusRequest request) {
        return payments.updateStatus(id, request.status());
    }
}
