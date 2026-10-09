package com.ironoak.domain;

import com.ironoak.domain.enums.BookingStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * A scheduled visit for one service offering. Cancelling sets a status; rows
 * are never deleted.
 */
@Entity
@Table(name = "service_booking")
public class ServiceBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @NotNull
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_offering_id", nullable = false)
    @NotNull
    private ServiceOffering serviceOffering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @Column(name = "location_address", nullable = false, columnDefinition = "text")
    @NotBlank
    private String locationAddress;

    @Column(name = "scheduled_at", nullable = false)
    @NotNull
    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "booking_status")
    @NotNull
    private BookingStatus status = BookingStatus.REQUESTED;

    @Column(name = "machine_model", length = 100)
    @Size(max = 100)
    private String machineModel;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ServiceBooking() {
    }

    public ServiceBooking(Customer customer,
            ServiceOffering serviceOffering,
            String locationAddress,
            OffsetDateTime scheduledAt,
            String machineModel,
            String notes) {
        this.customer = customer;
        this.serviceOffering = serviceOffering;
        this.locationAddress = locationAddress;
        this.scheduledAt = scheduledAt;
        this.machineModel = machineModel;
        this.notes = notes;
    }

    public void reschedule(OffsetDateTime newTime) {
        this.scheduledAt = newTime;
    }

    public void cancel(String reason) {
        this.status = BookingStatus.CANCELLED;
        this.cancelReason = reason;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public ServiceOffering getServiceOffering() {
        return serviceOffering;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public String getLocationAddress() {
        return locationAddress;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getMachineModel() {
        return machineModel;
    }

    public String getNotes() {
        return notes;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public void setServiceOffering(ServiceOffering serviceOffering) {
        this.serviceOffering = serviceOffering;
    }

    public void setLocationAddress(String locationAddress) {
        this.locationAddress = locationAddress;
    }

    public void setScheduledAt(OffsetDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public void setMachineModel(String machineModel) {
        this.machineModel = machineModel;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
