package com.ironoak.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "material_category")
public class MaterialCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected MaterialCategory() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
