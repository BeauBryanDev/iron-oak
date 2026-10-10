package com.ironoak.mapper;

import com.ironoak.domain.Payment;
import com.ironoak.dto.response.PaymentResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getProviderReference(),
                payment.getCheckoutSessionId(),
                payment.getPaymentIntentId(),
                payment.getFailureCode(),
                payment.getFailureMessage(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getPaidAt());
    }

    public List<PaymentResponse> toResponses(List<Payment> payments) {
        return payments.stream().map(this::toResponse).toList();
    }
}
