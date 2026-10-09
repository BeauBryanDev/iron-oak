package com.ironoak.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Guest identity captured at checkout - contact info only, never an authenticated
 * account. Staff logins live in {@link AdminUser} instead.
 */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 200)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Customer() {
    }

    public Customer(String name, String email, String phone) {
        this.name = name;
        // Stored lowercase: uq_customer_email is a unique index on lower(email). A blank
        // email becomes null so that several customers without one do not collide.
        this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        this.phone = phone;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
