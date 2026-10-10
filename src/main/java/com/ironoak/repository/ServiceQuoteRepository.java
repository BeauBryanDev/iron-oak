package com.ironoak.repository;

import com.ironoak.domain.ServiceQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ServiceQuoteRepository
        extends JpaRepository<ServiceQuote, Long>, JpaSpecificationExecutor<ServiceQuote> {

    /** Locks the row so two staff members cannot price the same request twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ServiceQuote q where q.id = :id")
    Optional<ServiceQuote> findLockedById(@Param("id") Long id);
}
