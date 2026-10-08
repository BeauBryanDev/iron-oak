package com.ironoak.domain;

import com.ironoak.domain.enums.OrderItemType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * One line of a mixed cart. itemType discriminates which of the three FK columns is
 * set; chk_item_reference in the schema enforces that exactly one of them is non-null.
 * quantity is a whole number of units (products, machines, service units).
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "item_type", nullable = false, columnDefinition = "order_item_type")
    private OrderItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_offering_id")
    private ServiceOffering serviceOffering;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milling_machine_id")
    private MillingMachine millingMachine;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "estimated_hours", precision = 4, scale = 1)
    private BigDecimal estimatedHours;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    protected OrderItem() {
    }

    public Long getId() {
        return id;
    }

    public OrderItemType getItemType() {
        return itemType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
