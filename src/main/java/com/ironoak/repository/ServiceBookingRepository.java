package com.ironoak.repository;

import com.ironoak.domain.ServiceBooking;
import com.ironoak.domain.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, Long>, JpaSpecificationExecutor<ServiceBooking> {

    /**
     * Loads the booking with the customer and service it names, for response
     * mapping.
     */
    @EntityGraph(attributePaths = { "customer", "serviceOffering" })
    Optional<ServiceBooking> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = "serviceOffering")
    List<ServiceBooking> findByCustomerIdOrderByScheduledAtDesc(Long customerId);

    List<ServiceBooking> findByOrderId(Long orderId);

    /** The technician queue: e.g. REQUESTED and CONFIRMED visits, soonest first. */
    @EntityGraph(attributePaths = { "customer", "serviceOffering" })
    Page<ServiceBooking> findByStatusInOrderByScheduledAtAsc(Collection<BookingStatus> statuses, Pageable pageable);

    /** Filtered staff list; loads the customer and service up front for response mapping. */
    @Override
    @EntityGraph(attributePaths = { "customer", "serviceOffering" })
    Page<ServiceBooking> findAll(Specification<ServiceBooking> spec, Pageable pageable);

    long countByStatus(BookingStatus status);

    boolean existsByCustomerId(Long customerId);
}
