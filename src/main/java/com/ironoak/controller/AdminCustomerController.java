package com.ironoak.controller;

import com.ironoak.dto.request.CustomerFilter;
import com.ironoak.dto.response.CustomerResponse;
import com.ironoak.services.AdminCustomerService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only staff view of guest customers (ROLE_ADMIN via /api/admin/**). */
@RestController
@RequestMapping("/api/admin/customers")
public class AdminCustomerController {

    private final AdminCustomerService customers;

    public AdminCustomerController(AdminCustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public PagedModel<CustomerResponse> list(
            CustomerFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new PagedModel<>(customers.list(filter, pageable));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return customers.get(id);
    }
}
