package com.ironoak.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "service_offering_category")
public class ServiceOfferingCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, 
        unique = true, length = 100)
    @NotBlank
    @Size(max = 100)
    private String name;

    protected ServiceOfferingCategory() {
    }

    public ServiceOfferingCategory(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }
}
