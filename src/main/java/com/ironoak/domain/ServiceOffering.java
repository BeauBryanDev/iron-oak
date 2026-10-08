package com.ironoak.domain;

import com.ironoak.domain.enums.PricingType;
import jakarta.persistence.*;
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
    private ServiceOfferingCategory category;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "pricing_type", nullable = false, 
    columnDefinition = "pricing_type")
    private PricingType pricingType;

    @Column(name = "fixed_price", precision = 10, scale = 2)
    private BigDecimal fixedPrice;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "estimated_min_hours", precision = 4, scale = 1)
    private BigDecimal estimatedMinHours;

    @Column(name = "estimated_max_hours", precision = 4, scale = 1)
    private BigDecimal estimatedMaxHours;

    @Column(name = "price_unit", length = 30)
    private String priceUnit;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    protected ServiceOffering() {
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
}
