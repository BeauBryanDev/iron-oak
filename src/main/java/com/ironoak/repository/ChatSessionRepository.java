package com.ironoak.repository;

import com.ironoak.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    boolean existsByCustomerId(Long customerId);
}
