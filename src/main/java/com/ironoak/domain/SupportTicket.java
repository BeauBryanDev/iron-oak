package com.ironoak.domain;

import com.ironoak.domain.enums.TicketStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** Created when Piper escalates a conversation to a human. */
@Entity
@Table(name = "support_ticket")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "customer_email", length = 200)
    @Size(max = 200)
    private String customerEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id")
    private ChatSession chatSession;

    @Column(nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String reason;

    @Column(nullable = false, columnDefinition = "text")
    @NotBlank
    private String summary;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "ticket_status")
    @NotNull
    private TicketStatus status = TicketStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SupportTicket() {
    }

    public SupportTicket(Customer customer,
            String customerEmail,
            ChatSession chatSession,
            String reason,
            String summary) {
        this.customer = customer;
        this.customerEmail = customerEmail;
        this.chatSession = chatSession;
        this.reason = reason;
        this.summary = summary;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public ChatSession getChatSession() {
        return chatSession;
    }

    public String getReason() {
        return reason;
    }

    public String getSummary() {
        return summary;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public void setChatSession(ChatSession chatSession) {
        this.chatSession = chatSession;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
