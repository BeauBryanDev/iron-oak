package com.ironoak.repository;

import com.ironoak.domain.Complaint;
import com.ironoak.domain.enums.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    Page<Complaint> findByStatus(ComplaintStatus status, Pageable pageable);

    long countByStatus(ComplaintStatus status);
}
