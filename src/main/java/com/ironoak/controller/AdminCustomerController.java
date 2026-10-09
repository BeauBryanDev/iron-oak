package com.ironoak.controller;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import com.ironoak.dto.request.PatchCustomerRequest;
import com.ironoak.dto.request.CustomerRequest;
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

/** Staff customer management (ROLE_ADMIN via /api/admin/**). */
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CustomerRequest request) {
        return customers.create(request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(id, request);
    }

    @PatchMapping("/{id}")
    public CustomerResponse patch(@PathVariable Long id, @Valid @RequestBody PatchCustomerRequest request) {
        return customers.patch(id, request);
    }

    /** Only a customer nothing refers to can be deleted; otherwise 409. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customers.delete(id);
    }
}
