package com.ironoak.domain;

import com.ironoak.domain.enums.ShippingSource;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Road distance from the Bogota warehouse to one destination city (see V7). */
@Entity
@Table(name = "shipping_route")
public class ShippingRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2)
    @NotBlank
    @Size(max = 2)
    private String country;

    @Column(name = "city_key", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String cityKey;

    @Column(name = "city_name", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String cityName;

    @Column(name = "distance_km", nullable = false, precision = 8, scale = 1)
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull
    private ShippingSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ShippingRoute() {
    }

    public ShippingRoute(String country,
            String cityKey,
            String cityName,
            BigDecimal distanceKm,
            ShippingSource source) {

        this.country = country;
        this.cityKey = cityKey;
        this.cityName = cityName;
        this.distanceKm = distanceKm;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCityKey() {
        return cityKey;
    }

    public void setCityKey(String cityKey) {
        this.cityKey = cityKey;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public ShippingSource getSource() {
        return source;
    }

    public void setSource(ShippingSource source) {
        this.source = source;
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
}
