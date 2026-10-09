package com.ironoak.domain;

import com.ironoak.domain.enums.OrderItemType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * One line of a mixed cart. itemType discriminates which of the three FK
 * columns is
 * set; chk_item_reference in the schema enforces that exactly one of them is
 * non-null.
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
    @NotNull
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "item_type", nullable = false, columnDefinition = "order_item_type")
    @NotNull
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
    @NotNull
    @Min(1)
    private Integer quantity;

    @Column(name = "estimated_hours", precision = 4, scale = 1)
    private BigDecimal estimatedHours;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal subtotal;

    protected OrderItem() {
    }

    private OrderItem(OrderItemType itemType,
            int quantity,
            BigDecimal estimatedHours,
            BigDecimal unitPrice) {
        this.itemType = itemType;
        this.quantity = quantity;
        this.estimatedHours = estimatedHours;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public static OrderItem ofProduct(Product product, int quantity) {
        OrderItem item = new OrderItem(OrderItemType.PRODUCT, quantity, null, product.getPrice());
        item.product = product;
        return item;
    }

    public static OrderItem ofMachine(MillingMachine machine, int quantity) {
        OrderItem item = new OrderItem(OrderItemType.MACHINE, quantity, null, machine.getPrice());
        item.millingMachine = machine;
        return item;
    }

    /**
     * unitPrice is the price of one unit of the service: the fixed price, or hourly
     * rate x hours.
     */
    public static OrderItem ofService(ServiceOffering service, int quantity,
            BigDecimal estimatedHours, BigDecimal unitPrice) {
        OrderItem item = new OrderItem(OrderItemType.SERVICE,
                quantity, estimatedHours, unitPrice);
        item.serviceOffering = service;
        return item;
    }

    void attachTo(CustomerOrder order) {
        this.order = order;
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public OrderItemType getItemType() {
        return itemType;
    }

    public Product getProduct() {
        return product;
    }

    public ServiceOffering getServiceOffering() {
        return serviceOffering;
    }

    public MillingMachine getMillingMachine() {
        return millingMachine;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrder(CustomerOrder order) {
        this.order = order;
    }

    public void setItemType(OrderItemType itemType) {
        this.itemType = itemType;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public void setServiceOffering(ServiceOffering serviceOffering) {
        this.serviceOffering = serviceOffering;
    }

    public void setMillingMachine(MillingMachine millingMachine) {
        this.millingMachine = millingMachine;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
