package com.ironoak.repository;

import com.ironoak.domain.Payment;
import com.ironoak.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /** Resolves a provider webhook to its payment row (uq_payment_provider_ref). */
    Optional<Payment> findByProviderAndProviderReference(String provider, String providerReference);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.order.id = :orderId and p.status = :status")
    BigDecimal sumAmountByOrderAndStatus(@Param("orderId") Long orderId,
            @Param("status") PaymentStatus status);

    Optional<Payment> findByCheckoutSessionId(String checkoutSessionId);

    Optional<Payment> findByPaymentIntentId(String paymentIntentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByOrderIdAndStatusIn(Long orderId,
            java.util.Collection<PaymentStatus> statuses);
}
