package com.ironoak.domain;

import com.ironoak.domain.enums.PricingType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Labor catalog. The chk_pricing constraint in V1__init_schema.sql enforces that
 * FIXED rows carry fixedPrice and HOURLY rows carry hourlyRate plus the hour range.
 */
@Entity
@Table(name = "service")
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_category_id", nullable = false)
    private ServiceCategory serviceCategory;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    // NAMED_ENUM binds to the native PostgreSQL enum type rather than a varchar.
    // columnDefinition names that type explicitly so ddl-auto: validate matches it.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "pricing_type", nullable = false, columnDefinition = "pricing_type")
    private PricingType pricingType;

    @Column(name = "fixed_price", precision = 10, scale = 2)
    private BigDecimal fixedPrice;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "estimated_min_hours", precision = 4, scale = 1)
    private BigDecimal estimatedMinHours;

    @Column(name = "estimated_max_hours", precision = 4, scale = 1)
    private BigDecimal estimatedMaxHours;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    protected Service() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
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
}
