package com.ironoak.domain;

import com.ironoak.domain.enums.PricingType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

// Named ServiceOffering, not Service, to avoid clashing with Spring's @Service stereotype.
@Entity
@Table(name = "service_offering")
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_offering_category_id", nullable = false)
    @NotNull
    private ServiceOfferingCategory category;

    @Column(nullable = false, unique = true, length = 50)
    @NotBlank
    @Size(max = 50)
    private String code;

    @Column(nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "pricing_type", nullable = false, columnDefinition = "pricing_type")
    @NotNull
    private PricingType pricingType;

    @Column(name = "fixed_price", precision = 10, scale = 2)
    @DecimalMin(value = "0.0")
    private BigDecimal fixedPrice;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    @DecimalMin(value = "0.0")
    private BigDecimal hourlyRate;

    @Column(name = "estimated_min_hours", precision = 4, scale = 1)
    private BigDecimal estimatedMinHours;

    @Column(name = "estimated_max_hours", precision = 4, scale = 1)
    private BigDecimal estimatedMaxHours;

    @Column(name = "price_unit", length = 30)
    @Size(max = 30)
    private String priceUnit;

    @Column(name = "is_active", nullable = false)
    @NotNull
    private Boolean isActive = true;

    protected ServiceOffering() {
    }

    public ServiceOffering(ServiceOfferingCategory category, String code,
            String name,
            String description,
            PricingType pricingType,
            BigDecimal fixedPrice,
            BigDecimal hourlyRate,
            BigDecimal estimatedMinHours,
            BigDecimal estimatedMaxHours,
            String priceUnit) {
        this.category = category;
        this.code = code;
        this.name = name;
        this.description = description;
        this.pricingType = pricingType;
        this.fixedPrice = fixedPrice;
        this.hourlyRate = hourlyRate;
        this.estimatedMinHours = estimatedMinHours;
        this.estimatedMaxHours = estimatedMaxHours;
        this.priceUnit = priceUnit;
    }

    public Long getId() {
        return id;
    }

    public ServiceOfferingCategory getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public PricingType getPricingType() {
        return pricingType;
    }

    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public BigDecimal getEstimatedMinHours() {
        return estimatedMinHours;
    }

    public BigDecimal getEstimatedMaxHours() {
        return estimatedMaxHours;
    }

    public String getPriceUnit() {
        return priceUnit;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCategory(ServiceOfferingCategory category) {
        this.category = category;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPricingType(PricingType pricingType) {
        this.pricingType = pricingType;
    }

    public void setFixedPrice(BigDecimal fixedPrice) {
        this.fixedPrice = fixedPrice;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public void setEstimatedMinHours(BigDecimal estimatedMinHours) {
        this.estimatedMinHours = estimatedMinHours;
    }

    public void setEstimatedMaxHours(BigDecimal estimatedMaxHours) {
        this.estimatedMaxHours = estimatedMaxHours;
    }

    public void setPriceUnit(String priceUnit) {
        this.priceUnit = priceUnit;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
