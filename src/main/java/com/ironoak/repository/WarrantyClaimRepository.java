package com.ironoak.repository;

import com.ironoak.domain.WarrantyClaim;
import com.ironoak.domain.enums.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, Long>, JpaSpecificationExecutor<WarrantyClaim> {

    List<WarrantyClaim> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<WarrantyClaim> findByOrderId(Long orderId);

    /**
     * Friendly pre-check before inserting: the database also enforces one OPEN or
     * IN_REVIEW claim per order item (uq_warranty_claim_active_item).
     */
    boolean existsByOrderItemIdAndStatusIn(Long orderItemId, Collection<ClaimStatus> statuses);

    /** The admin queue, newest first when sorted by createdAt. */
    Page<WarrantyClaim> findByStatus(ClaimStatus status, Pageable pageable);

    long countByStatus(ClaimStatus status);

    boolean existsByCustomerId(Long customerId);
}
