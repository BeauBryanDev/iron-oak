package com.ironoak.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Sold by area (m2), not by unit - flooring and wall cladding. */
@Entity
@Table(name = "surface_material")
public class SurfaceMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_category_id", nullable = false)
    private MaterialCategory materialCategory;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "material_type", nullable = false, length = 50)
    private String materialType;

    @Column(name = "price_per_sqm", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSqm;

    @Column(name = "stock_sqm", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockSqm;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    protected SurfaceMaterial() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPricePerSqm() {
        return pricePerSqm;
    }
}
