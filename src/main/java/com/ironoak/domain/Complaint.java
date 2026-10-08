package com.ironoak.domain;

import com.ironoak.domain.enums.ComplaintStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "complaint")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Column(name = "complaint_datetime", nullable = false)
    private OffsetDateTime complaintDatetime;

    @Column(nullable = false, length = 200)
    private String product;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "complaint_status")
    private ComplaintStatus status = ComplaintStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Complaint() {
    }

    public Complaint(String customerName, OffsetDateTime complaintDatetime,
                     String product, String description) {
        this.customerName = customerName;
        this.complaintDatetime = complaintDatetime;
        this.product = product;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public OffsetDateTime getComplaintDatetime() {
        return complaintDatetime;
    }

    public String getProduct() {
        return product;
    }

    public String getDescription() {
        return description;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public void setStatus(ComplaintStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
