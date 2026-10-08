package com.ironoak.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The database enforces (tool_category_id, vision_name) -> (tool_category.id, model_label).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_category_id", nullable = false)
    private ToolCategory toolCategory;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(name = "vision_name", nullable = false, length = 100)
    private String visionName;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String brand;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "warranty_months", nullable = false)
    private Integer warrantyMonths;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, 
    insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Product() {
    }

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

    public String getImageUrl() {
        return imageUrl;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
