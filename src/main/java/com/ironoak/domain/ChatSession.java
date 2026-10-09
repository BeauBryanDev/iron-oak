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

    /** customer may be null: anonymous visitors can chat too. */
    public ChatSession(Customer customer) {
        this.customer = customer;
        this.startedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }
}
