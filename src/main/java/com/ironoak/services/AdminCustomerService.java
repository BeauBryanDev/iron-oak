package com.ironoak.services;

import com.ironoak.domain.Customer;
import com.ironoak.dto.request.CustomerFilter;
import com.ironoak.dto.response.CustomerResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.CustomerMapper;
import com.ironoak.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only staff view of guest customers. Customers are created only through checkout, bookings and tickets. */
@Service
@Transactional(readOnly = true)
public class AdminCustomerService {

    private final CustomerRepository customers;
    private final CustomerMapper mapper;

    public AdminCustomerService(CustomerRepository customers, CustomerMapper mapper) {
        this.customers = customers;
        this.mapper = mapper;
    }

    public Page<CustomerResponse> list(CustomerFilter filter, Pageable pageable) {
        Specification<Customer> spec = Specification.allOf(
                FilterSpecs.dateRange("createdAt", filter.from(), filter.to()),
                !FilterSpecs.hasText(filter.q()) ? null
                        : (root, query, cb) -> FilterSpecs.anyContains(cb, filter.q(),
                                root.<String>get("name"), root.<String>get("email"), root.<String>get("phone")));
        return customers.findAll(spec, pageable).map(mapper::toResponse);
    }

    public CustomerResponse get(Long id) {
        return customers.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }
}
