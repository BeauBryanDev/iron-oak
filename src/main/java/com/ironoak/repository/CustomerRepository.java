package com.ironoak.repository;

import com.ironoak.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** Email is optional and not unique; this returns the first match for guest lookup. */
    Optional<Customer> findFirstByEmailIgnoreCase(String email);
}
