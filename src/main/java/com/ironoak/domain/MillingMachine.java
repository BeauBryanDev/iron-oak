package com.ironoak.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Entity
@Table(name = "milling_machine")
public class MillingMachine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_code", nullable = false, unique = true, length = 30)
    @NotBlank
    @Size(max = 30)
    private String modelCode;

    @Column(nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "power_kw", nullable = false, precision = 5, scale = 1)
    @NotNull
    @Positive
    private BigDecimal powerKw;

    @Column(name = "spindle_min_rpm", nullable = false)
    @NotNull
    @Positive
    private Integer spindleMinRpm;

    @Column(name = "spindle_max_rpm", nullable = false)
    @NotNull
    @Positive
    private Integer spindleMaxRpm;

    @Column(name = "table_length_mm", nullable = false)
    @NotNull
    @Positive
    private Integer tableLengthMm;

    @Column(name = "table_width_mm", nullable = false)
    @NotNull
    @Positive
    private Integer tableWidthMm;

    @Column(nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal price;

    @Column(name = "warranty_months", nullable = false)
    @NotNull
    @Min(0)
    private Integer warrantyMonths;

    @Column(name = "image_url", length = 500)
    @Size(max = 500)
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    @NotNull
    private Boolean isActive = true;

    protected MillingMachine() {
    }

    public MillingMachine(String modelCode,
            String name,
            String description,
            BigDecimal powerKw,
            Integer spindleMinRpm,
            Integer spindleMaxRpm,
            Integer tableLengthMm,
            Integer tableWidthMm,
            BigDecimal price,
            Integer warrantyMonths,
            String imageUrl) {
        this.modelCode = modelCode;
        this.name = name;
        this.description = description;
        this.powerKw = powerKw;
        this.spindleMinRpm = spindleMinRpm;
        this.spindleMaxRpm = spindleMaxRpm;
        this.tableLengthMm = tableLengthMm;
        this.tableWidthMm = tableWidthMm;
        this.price = price;
        this.warrantyMonths = warrantyMonths;
        this.imageUrl = imageUrl;
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

    public void setId(Long id) {
        this.id = id;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPowerKw(BigDecimal powerKw) {
        this.powerKw = powerKw;
    }

    public void setSpindleMinRpm(Integer spindleMinRpm) {
        this.spindleMinRpm = spindleMinRpm;
    }

    public void setSpindleMaxRpm(Integer spindleMaxRpm) {
        this.spindleMaxRpm = spindleMaxRpm;
    }

    public void setTableLengthMm(Integer tableLengthMm) {
        this.tableLengthMm = tableLengthMm;
    }

    public void setTableWidthMm(Integer tableWidthMm) {
        this.tableWidthMm = tableWidthMm;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setWarrantyMonths(Integer warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
