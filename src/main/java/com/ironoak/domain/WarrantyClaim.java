package com.ironoak.domain;

import com.ironoak.domain.enums.ClaimStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * A warranty claim on one order line. The database enforces that orderItemId belongs to
 * the order (composite foreign key) and that only one open claim exists per line.
 */
@Entity
@Table(name = "warranty_claim")
public class WarrantyClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "claim_status")
    private ClaimStatus status = ClaimStatus.OPEN;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected WarrantyClaim() {
    }

    public WarrantyClaim(Customer customer, CustomerOrder order, Long orderItemId, String description) {
        this.customer = customer;
        this.order = order;
        this.orderItemId = orderItemId;
        this.description = description;
    }

    /** A review step (IN_REVIEW, APPROVED) that is not yet the end of the claim. */
    public void review(ClaimStatus newStatus, String note) {
        this.status = newStatus;
        if (note != null) {
            this.resolutionNote = note;
        }
    }

    public void resolve(ClaimStatus finalStatus, String note) {
        this.status = finalStatus;
        this.resolutionNote = note;
        this.resolvedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public String getDescription() {
        return description;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}
