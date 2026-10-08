package com.ironoak.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "milling_machine")
public class MillingMachine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_code", nullable = false, unique = true, length = 30)
    private String modelCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "power_kw", nullable = false, precision = 5, scale = 1)
    private BigDecimal powerKw;

    @Column(name = "spindle_min_rpm", nullable = false)
    private Integer spindleMinRpm;

    @Column(name = "spindle_max_rpm", nullable = false)
    private Integer spindleMaxRpm;

    @Column(name = "table_length_mm", nullable = false)
    private Integer tableLengthMm;

    @Column(name = "table_width_mm", nullable = false)
    private Integer tableWidthMm;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "warranty_months", nullable = false)
    private Integer warrantyMonths;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    protected MillingMachine() {
    }

    public Long getId() {
        return id;
    }

    public String getModelCode() {
        return modelCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPowerKw() {
        return powerKw;
    }

    public Integer getSpindleMinRpm() {
        return spindleMinRpm;
    }

    public Integer getSpindleMaxRpm() {
        return spindleMaxRpm;
    }

    public Integer getTableLengthMm() {
        return tableLengthMm;
    }

    public Integer getTableWidthMm() {
        return tableWidthMm;
    }

    public BigDecimal getPrice() {
        return price;
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
}
