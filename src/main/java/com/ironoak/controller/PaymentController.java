package com.ironoak.controller;

import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import com.ironoak.services.StripeRefundService;
import com.ironoak.security.ClientIpResolver;
import com.ironoak.dto.request.RefundPaymentRequest;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import com.ironoak.dto.request.PaymentFilter;
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
 * Staff-only for now. A public checkout or provider webhook endpoint needs the
 * provider's
 * signature verification, which belongs with the payment-provider integration.
 */
@RestController
public class PaymentController {

    private final PaymentService payments;
    private final StripeRefundService refunds;
    private final ClientIpResolver clientIp;

    public PaymentController(PaymentService payments,
            StripeRefundService refunds,
            ClientIpResolver clientIp) {

        this.payments = payments;
        this.refunds = refunds;
        this.clientIp = clientIp;
    }

    @PostMapping("/api/admin/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request) {

        return payments.create(request);
    }

    @GetMapping("/api/admin/payments")
    public PagedModel<PaymentResponse> list(
            PaymentFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return new PagedModel<>(payments.list(filter, pageable));
    }

    @GetMapping("/api/admin/payments/{id}")
    public PaymentResponse get(@PathVariable Long id) {

        return payments.get(id);
    }

    @GetMapping("/api/admin/orders/{orderId}/payments")
    public List<PaymentResponse> forOrder(@PathVariable Long orderId) {

        return payments.listForOrder(orderId);
    }

    /**
     * Staff approval of a full refund: returns the money through Stripe and marks
     * the payment REFUNDED.
     */
    @PostMapping("/api/admin/payments/{id}/refund")
    public PaymentResponse refund(@PathVariable Long id,
            @Valid @RequestBody(required = false) RefundPaymentRequest request,
            Principal principal, HttpServletRequest http) {

        return refunds.refund(id, request == null ? null : request.reason(),
                principal.getName(), clientIp.resolve(http));
    }

    @PatchMapping("/api/admin/payments/{id}/status")
    public PaymentResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdatePaymentStatusRequest request) {

        return payments.updateStatus(id, request.status());
    }
}
