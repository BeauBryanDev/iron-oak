package com.ironoak.controller;

import com.ironoak.dto.request.CreateServiceQuoteRequest;
import com.ironoak.dto.request.DeclineServiceQuoteRequest;
import com.ironoak.dto.request.PriceServiceQuoteRequest;
import com.ironoak.dto.request.ServiceQuoteFilter;
import com.ironoak.dto.response.ServiceQuoteResponse;
import com.ironoak.services.ServiceQuoteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quote requests for QUOTE-priced services. Customers create one and read it
 * back with the
 * email on it (?email=); staff list, price (which creates the order to pay) or
 * decline them.
 */
@RestController
public class ServiceQuoteController {

    private final ServiceQuoteService quotes;

    public ServiceQuoteController(ServiceQuoteService quotes) {
        this.quotes = quotes;
    }

    @PostMapping("/api/service-quotes")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceQuoteResponse request(@Valid @RequestBody CreateServiceQuoteRequest request) {

        return quotes.request(request);
    }

    @GetMapping("/api/service-quotes/{id}")
    public ServiceQuoteResponse get(@PathVariable Long id, @RequestParam String email) {

        return quotes.getForCustomer(id, email);
    }

    @GetMapping("/api/admin/service-quotes")
    public PagedModel<ServiceQuoteResponse> list(
            ServiceQuoteFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return new PagedModel<>(quotes.list(filter, pageable));
    }

    @GetMapping("/api/admin/service-quotes/{id}")
    public ServiceQuoteResponse getForStaff(@PathVariable Long id) {

        return quotes.get(id);
    }

    @PostMapping("/api/admin/service-quotes/{id}/quote")
    public ServiceQuoteResponse price(@PathVariable Long id, @Valid @RequestBody PriceServiceQuoteRequest request) {

        return quotes.price(id, request.price(), request.note());
    }

    @PostMapping("/api/admin/service-quotes/{id}/decline")
    public ServiceQuoteResponse decline(@PathVariable Long id,
            @Valid @RequestBody DeclineServiceQuoteRequest request) {

        return quotes.decline(id, request.note());
    }
}
