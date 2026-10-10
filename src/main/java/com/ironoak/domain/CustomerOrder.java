package com.ironoak.domain;

import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * An order header with the buyer's contact and shipping details copied onto it
 * (the customer
 * row can change later; the order must not). The database enforces
 * grand_total = subtotal + shipping_cost + taxes.
 *
 * Web and Piper orders start PENDING_PAYMENT and hold their product stock until
 * reservationExpiresAt; staff-entered orders start CONFIRMED.
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    private static final String ORDER_NUMBER_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public, non-guessable reference such as IO-20261009-K7QZ4M2P. */
    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    @NotBlank
    @Size(max = 40)
    private String orderNumber;

    // nullable: guest checkout is the default
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "order_status", nullable = false, columnDefinition = "order_status")
    @NotNull
    private OrderStatus status;

    // lets the dashboard show where an order came from (web checkout, Piper, staff)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "channel", nullable = false, columnDefinition = "order_channel")
    @NotNull
    private OrderChannel channel;

    @Column(name = "customer_name", length = 200)
    @Size(max = 200)
    private String customerName;

    /** Stored trimmed and lowercase, like Customer.email. */
    @Column(name = "customer_email", length = 254)
    @Size(max = 254)
    private String customerEmail;

    @Column(name = "phone_number", length = 30)
    @Size(max = 30)
    private String phoneNumber;

    /** ISO 3166-1 alpha-2, upper case. */
    @Column(length = 2)
    @Pattern(regexp = "[A-Z]{2}")
    private String country;

    @Column(length = 100)
    @Size(max = 100)
    private String province;

    @Column(length = 100)
    @Size(max = 100)
    private String city;

    @Column(name = "shipping_address", columnDefinition = "text")
    private String shippingAddress;

    @Column(nullable = false, precision = 12, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal taxes = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal grandTotal = BigDecimal.ZERO;

    /** ISO 4217, upper case. */
    @Column(nullable = false, length = 3)
    @NotNull
    @Pattern(regexp = "[A-Z]{3}")
    private String currency = "USD";

    /**
     * True while this order holds product stock that was taken from inventory and
     * not yet returned.
     */
    @Column(name = "stock_reserved", nullable = false)
    private boolean stockReserved = false;

    /**
     * For PENDING_PAYMENT orders: when the held stock is released if still unpaid.
     */
    @Column(name = "reservation_expires_at")
    private OffsetDateTime reservationExpiresAt;

    // Lets create_order be retried safely: the same key never creates a second
    // order.
    @Column(name = "idempotency_key", unique = true, length = 100)
    @Size(max = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(Customer customer, OrderChannel channel) {
        this.customer = customer;
        this.channel = channel;
        this.status = channel == OrderChannel.ADMIN_MANUAL ? OrderStatus.CONFIRMED : OrderStatus.PENDING_PAYMENT;
        this.orderNumber = generateOrderNumber();
        this.createdAt = OffsetDateTime.now();
    }

    /**
     * IO-YYYYMMDD-XXXXXXXX with 8 random characters (no 0/O/1/I/L), unique by
     * constraint.
     */
    static String generateOrderNumber() {
        StringBuilder suffix = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            suffix.append(ORDER_NUMBER_ALPHABET.charAt(RANDOM.nextInt(ORDER_NUMBER_ALPHABET.length())));
        }
        return "IO-" + DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(ZoneOffset.UTC)) + "-" + suffix;
    }

    /** Adds a line and keeps subtotal and grandTotal in step. */
    public void addItem(OrderItem item) {
        item.attachTo(this);
        items.add(item);
        recalculateTotals();
    }

    /**
     * subtotal = sum of the lines; grandTotal = subtotal + shippingCost + taxes.
     */
    public void recalculateTotals() {
        subtotal = items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        grandTotal = subtotal.add(shippingCost).add(taxes);
    }

    /**
     * Copies the buyer's details onto the order; the email is normalised like
     * Customer.email.
     */
    public void setContact(String name,
            String email,
            String phone) {
        this.customerName = name;
        setCustomerEmail(email);
        this.phoneNumber = phone;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public OrderChannel getChannel() {
        return channel;
    }

    public void setChannel(OrderChannel channel) {
        this.channel = channel;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail == null || customerEmail.isBlank()
                ? null
                : customerEmail.trim().toLowerCase();
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }

    public BigDecimal getTaxes() {
        return taxes;
    }

    public void setTaxes(BigDecimal taxes) {
        this.taxes = taxes;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isStockReserved() {
        return stockReserved;
    }

    public void setStockReserved(boolean stockReserved) {
        this.stockReserved = stockReserved;
    }

    public OffsetDateTime getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public void setReservationExpiresAt(OffsetDateTime reservationExpiresAt) {
        this.reservationExpiresAt = reservationExpiresAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
