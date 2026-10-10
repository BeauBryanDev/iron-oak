package com.ironoak.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The database enforces (tool_category_id, vision_name) -> (tool_category.id,
    // model_label).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_category_id", nullable = false)
    @NotNull
    private ToolCategory toolCategory;

    @Column(nullable = false, unique = true, length = 50)
    @NotNull
    @Size(max = 50)
    private String sku;

    @Column(name = "vision_name", nullable = false, length = 100)
    @NotNull
    @Size(max = 100)
    private String visionName;

    @Column(nullable = false, length = 200)
    @NotNull
    @Size(max = 200)
    private String name;

    @Column(length = 100)
    @Size(max = 100)
    private String brand;

    @Column(nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    @NotNull
    @Min(0)
    private Integer stockQuantity;

    @Column(name = "warranty_months", nullable = false)
    @NotNull
    @Min(0)
    private Integer warrantyMonths = 0;

    /**
     * Shipping weight of one unit, in kg. Null until entered (see
     * ShippingCalculator).
     */
    @Column(name = "weight_kg", precision = 8, scale = 3)
    @Positive
    private BigDecimal weightKg;

    /** Shipping volume of one packed unit, in cubic metres. Null until entered. */
    @Column(name = "volume_m3", precision = 8, scale = 4)
    @Positive
    private BigDecimal volumeM3;

    @Column(name = "image_url", length = 500)
    @Size(max = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Constructors

    protected Product() {
    }

    public Product(
            ToolCategory toolCategory,
            String sku,
            String visionName,
            String name,
            String category,
            BigDecimal price,
            Integer stockQuantity) {

        this.toolCategory = toolCategory;
        this.sku = sku;
        this.visionName = visionName;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    // Getters

    public Long getId() {
        return id;
    }

    public ToolCategory getToolCategory() {
        return toolCategory;
    }

    public String getSku() {
        return sku;
    }

    public String getVisionName() {
        return visionName;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public Integer getWarrantyMonths() {
        return warrantyMonths;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getVolumeM3() {
        return volumeM3;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    // Setters

    public void setId(Long id) {
        this.id = id;
    }

    public void setToolCategory(ToolCategory toolCategory) {
        this.toolCategory = toolCategory;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setVisionName(String visionName) {
        this.visionName = visionName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public void setWarrantyMonths(Integer warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public void setVolumeM3(BigDecimal volumeM3) {
        this.volumeM3 = volumeM3;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // Business Methods
    public boolean hasStock(int quantity) {
        return stockQuantity != null && stockQuantity >= quantity;
    }

    public boolean isInStock() {
        return stockQuantity != null && stockQuantity > 0;
    }

}
