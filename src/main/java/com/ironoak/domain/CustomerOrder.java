package com.ironoak.domain;

import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // nullable: guest checkout is the default
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "order_status")
    @NotNull
    private OrderStatus status;

    // lets the dashboard show what Piper closed on its own
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "channel", nullable = false, columnDefinition = "order_channel")
    @NotNull
    private OrderChannel channel;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal totalAmount;

    // Lets create_order be retried safely: the same key never creates a second
    // order.
    @Column(name = "idempotency_key", unique = true, length = 100)
    @Size(max = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(Customer customer, OrderChannel channel) {
        this.customer = customer;
        this.channel = channel;
        this.status = OrderStatus.CONFIRMED;
        this.totalAmount = BigDecimal.ZERO;
        this.createdAt = OffsetDateTime.now();
    }

    /** Adds a line and keeps the running total in step. */
    public void addItem(OrderItem item) {
        item.attachTo(this);
        items.add(item);
        totalAmount = totalAmount.add(item.getSubtotal());
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public OrderChannel getChannel() {
        return channel;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public void setChannel(OrderChannel channel) {
        this.channel = channel;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
