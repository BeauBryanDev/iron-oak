package com.ironoak.repository;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.time.OffsetDateTime;
import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface CustomerOrderRepository
                extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {

        /**
         * Loads the order with its lines in one query, for responses that list items.
         */
        @EntityGraph(attributePaths = "items")
        Optional<CustomerOrder> findWithItemsById(Long id);

        @EntityGraph(attributePaths = "items")
        Optional<CustomerOrder> findByIdempotencyKey(String idempotencyKey);

        Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

        Page<CustomerOrder> findByCustomerId(Long customerId, Pageable pageable);

        long countByStatus(OrderStatus status);

        long countByChannel(OrderChannel channel);

        @Query("select coalesce(sum(o.grandTotal), 0) from CustomerOrder o where o.status = :status")
        BigDecimal sumTotalByStatus(@Param("status") OrderStatus status);

        boolean existsByCustomerId(Long customerId);

        boolean existsByOrderNumber(String orderNumber);

        /**
         * Loads the order and locks its row until the transaction ends (SELECT ... FOR
         * UPDATE), so a
         * staff cancel, the expiry job and an arriving payment cannot act on the same
         * order at once
         * (for example both returning its stock).
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select o from CustomerOrder o where o.id = :id")
        Optional<CustomerOrder> findLockedById(@Param("id") Long id);

        @EntityGraph(attributePaths = "items")
        Optional<CustomerOrder> findWithItemsByOrderNumber(String orderNumber);

        /**
         * Unpaid orders whose stock hold has run out, oldest first, for the expiry job.
         */
        @Query("select o.id from CustomerOrder o where o.status = :status and o.reservationExpiresAt < :now "
                        + "order by o.reservationExpiresAt")
        List<Long> findIdsByStatusAndReservationExpiredBefore(@Param("status") OrderStatus status,
                        @Param("now") OffsetDateTime now,
                        Pageable pageable);
}
