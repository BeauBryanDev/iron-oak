package com.ironoak.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_session")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // nullable: anonymous visitors can chat too
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    protected ChatSession() {
    }

    public Long getId() {
        return id;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }
}
