package com.ironoak.repository;

import com.ironoak.domain.SupportTicket;
import com.ironoak.domain.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long>, JpaSpecificationExecutor<SupportTicket> {

    /** The human-support queue, newest first when sorted by createdAt. */
    Page<SupportTicket> findByStatus(TicketStatus status, Pageable pageable);

    List<SupportTicket> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<SupportTicket> findByChatSessionId(Long chatSessionId);

    long countByStatus(TicketStatus status);
}
