package com.ironoak.repository;

import com.ironoak.domain.ChatMessage;
import com.ironoak.domain.enums.MessageRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** The newest messages of the given roles first; page size = how many. */
    List<ChatMessage> findByChatSessionIdAndRoleInOrderByIdDesc(Long chatSessionId,
            Collection<MessageRole> roles,
            Pageable pageable);

    /** A conversation in the order it was written. */
    List<ChatMessage> findByChatSessionIdAndRoleInOrderByIdAsc(Long chatSessionId,
            Collection<MessageRole> roles);

    long countByChatSessionIdAndRole(Long chatSessionId, MessageRole role);
}
