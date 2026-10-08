package com.ironoak.repository;

import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** Loads the order with its lines in one query, for responses that list items. */
    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findWithItemsById(Long id);

    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

    Page<CustomerOrder> findByCustomerId(Long customerId, Pageable pageable);

    long countByStatus(OrderStatus status);

    long countByChannel(OrderChannel channel);

    @Query("select coalesce(sum(o.totalAmount), 0) from CustomerOrder o where o.status = :status")
    BigDecimal sumTotalByStatus(@Param("status") OrderStatus status);
}
