package com.ironoak.domain;

import com.ironoak.domain.enums.ComplaintStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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
    @NotBlank
    @Size(max = 200)
    private String customerName;

    @Column(name = "complaint_datetime", nullable = false)
    @NotNull
    private OffsetDateTime complaintDatetime;

    @Column(nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String product;

    @Column(nullable = false, columnDefinition = "text")
    @NotBlank
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "complaint_status")
    @NotNull
    private ComplaintStatus status = ComplaintStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Complaint() {
    }

    public Complaint(String customerName,
            OffsetDateTime complaintDatetime,
            String product,
            String description) {
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

    public void setId(Long id) {
        this.id = id;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public void setComplaintDatetime(OffsetDateTime complaintDatetime) {
        this.complaintDatetime = complaintDatetime;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
