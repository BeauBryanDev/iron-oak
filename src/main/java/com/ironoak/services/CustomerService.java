package com.ironoak.services;

import com.ironoak.domain.Customer;
import com.ironoak.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Guest identity handling shared by orders, bookings and tickets. Customers never log in. */
@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /** Trims and lowercases; blank becomes null. Matches the lower(email) unique index. */
    public static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    /** True when the customer's email equals the given one, ignoring case. */
    public static boolean hasEmail(Customer customer, String email) {
        String normalized = normalizeEmail(email);
        return customer != null && normalized != null && normalized.equals(customer.getEmail());
    }

    /**
     * Reuses the customer with this email, or creates one. Without an email there is
     * nothing to match on, so a new row is created each time.
     */
    public Customer findOrCreate(String name, String email, String phone) {
        String normalized = normalizeEmail(email);
        if (normalized != null) {
            Optional<Customer> existing = customers.findFirstByEmailIgnoreCase(normalized);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return customers.save(new Customer(name.trim(), normalized, phone));
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findByEmail(String email) {
        String normalized = normalizeEmail(email);
        return normalized == null ? Optional.empty() : customers.findFirstByEmailIgnoreCase(normalized);
    }
}
