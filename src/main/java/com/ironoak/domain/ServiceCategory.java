package com.ironoak.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "service_category")
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected ServiceCategory() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
