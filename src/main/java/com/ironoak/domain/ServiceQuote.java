package com.ironoak.domain;

import com.ironoak.domain.enums.ServiceQuoteStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;

/** A customer's request for a price on a QUOTE-type service (V10). */
@Entity
@Table(name = "service_quote")
public class ServiceQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_offering_id", nullable = false)
    @NotNull
    private ServiceOffering serviceOffering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "customer_name", nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String customerEmail;

    @Column(name = "customer_phone", length = 30)
    @Size(max = 30)
    private String customerPhone;

    @Column(nullable = false, length = 2)
    @NotBlank
    @Size(min = 2, max = 2)
    private String country;

    @Column(length = 100)
    @Size(max = 100)
    private String city;

    @Column(nullable = false, columnDefinition = "text")
    @NotBlank
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull
    private ServiceQuoteStatus status = ServiceQuoteStatus.REQUESTED;

    @Column(name = "quoted_price", precision = 10, scale = 2)
    @Positive
    private BigDecimal quotedPrice;

    @Column(name = "staff_note", columnDefinition = "text")
    private String staffNote;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", unique = true)
    private CustomerOrder order;

    @Column(name = "quoted_at")
    private OffsetDateTime quotedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ServiceQuote() {
    }

    public ServiceQuote(ServiceOffering serviceOffering,
            Customer customer, String customerName,
            String customerEmail,
            String customerPhone,
            String country,
            String city,
            String description) {

        this.serviceOffering = serviceOffering;
        this.customer = customer;
        this.customerName = customerName;
        this.customerEmail = customerEmail == null ? null : customerEmail.trim().toLowerCase(Locale.ROOT);
        this.customerPhone = customerPhone;
        this.country = country == null ? null : country.toUpperCase(Locale.ROOT);
        this.city = city;
        this.description = description;
    }

    /**
     * Staff priced the job; order is the PENDING_PAYMENT order created at that
     * price.
     */
    public void markQuoted(BigDecimal price,
            String note,
            CustomerOrder order) {

        this.status = ServiceQuoteStatus.QUOTED;
        this.quotedPrice = price;
        this.staffNote = note;
        this.order = order;
        this.quotedAt = OffsetDateTime.now();
    }

    public void decline(String note) {
        this.status = ServiceQuoteStatus.DECLINED;
        this.staffNote = note;
    }

    public Long getId() {
        return id;
    }

    public ServiceOffering getServiceOffering() {
        return serviceOffering;
    }

    public Customer getCustomer() {
        return customer;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public String getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public String getDescription() {
        return description;
    }

    public ServiceQuoteStatus getStatus() {
        return status;
    }

    public BigDecimal getQuotedPrice() {
        return quotedPrice;
    }

    public String getStaffNote() {
        return staffNote;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public OffsetDateTime getQuotedAt() {
        return quotedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
