package com.ironoak.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * An end mill (milling cutter,), a sellable item of its own (V13), separate
 * from the
 * MobileNet product catalog. visionLabel is the class name of the YOLOv11
 * end-mill
 * detector (unique: one class, one end mill). Deactivated, never deleted; no
 * warranty.
 * sku is unique in code only (no index, by design).
 */
@Entity
@Table(name = "end_mills")
public class EndMill {

    public static final String RIGHT_HAND = "Right-Hand";
    public static final String LEFT_HAND = "Left-Hand";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String sku;

    @Column(nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "flute_type", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String fluteType;

    @Column(name = "cut_direction", nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String cutDirection = RIGHT_HAND;

    @Column(name = "cutting_diameter_mm", nullable = false, precision = 5, scale = 2)
    @NotNull
    @Positive
    private BigDecimal cuttingDiameterMm;

    @Column(name = "radius_mm", nullable = false, precision = 5, scale = 2)
    @NotNull
    @PositiveOrZero
    private BigDecimal radiusMm;

    @Column(nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String material;

    @Column(name = "overall_length_mm", nullable = false, precision = 7, scale = 2)
    @NotNull
    @Positive
    private BigDecimal overallLengthMm;

    /** Grams (the shipping code converts to kg). Null until entered. */
    @Column(name = "weight_g", precision = 7, scale = 2)
    @Positive
    private BigDecimal weightG;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal price;

    @Column(name = "max_rpm")
    @Positive
    private Integer maxRpm;

    @Column(name = "stock_quantity", nullable = false)
    @NotNull
    @Min(0)
    private Integer stockQuantity = 0;

    @Column(name = "vision_label", length = 100, unique = true)
    @Size(max = 100)
    private String visionLabel;

    @Column(name = "image_url", length = 500)
    @Size(max = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected EndMill() {
    }

    public EndMill(String sku,
            String name,
            String fluteType,
            BigDecimal cuttingDiameterMm,
            BigDecimal radiusMm,
            String material,
            BigDecimal overallLengthMm,
            BigDecimal price,
            Integer stockQuantity) {

        this.sku = sku;
        this.name = name;
        this.fluteType = fluteType;
        this.cuttingDiameterMm = cuttingDiameterMm;
        this.radiusMm = radiusMm;
        this.material = material;
        this.overallLengthMm = overallLengthMm;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    // Business methods

    public boolean isInStock() {
        return stockQuantity != null && stockQuantity > 0;
    }

    // Getters

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getFluteType() {
        return fluteType;
    }

    public String getCutDirection() {
        return cutDirection;
    }

    public BigDecimal getCuttingDiameterMm() {
        return cuttingDiameterMm;
    }

    public BigDecimal getRadiusMm() {
        return radiusMm;
    }

    public String getMaterial() {
        return material;
    }

    public BigDecimal getOverallLengthMm() {
        return overallLengthMm;
    }

    public BigDecimal getWeightG() {
        return weightG;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getMaxRpm() {
        return maxRpm;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public String getVisionLabel() {
        return visionLabel;
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

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Setters

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setFluteType(String fluteType) {
        this.fluteType = fluteType;
    }

    public void setCutDirection(String cutDirection) {
        this.cutDirection = cutDirection;
    }

    public void setCuttingDiameterMm(BigDecimal cuttingDiameterMm) {
        this.cuttingDiameterMm = cuttingDiameterMm;
    }

    public void setRadiusMm(BigDecimal radiusMm) {
        this.radiusMm = radiusMm;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public void setOverallLengthMm(BigDecimal overallLengthMm) {
        this.overallLengthMm = overallLengthMm;
    }

    public void setWeightG(BigDecimal weightG) {
        this.weightG = weightG;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setMaxRpm(Integer maxRpm) {
        this.maxRpm = maxRpm;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public void setVisionLabel(String visionLabel) {
        this.visionLabel = visionLabel;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
