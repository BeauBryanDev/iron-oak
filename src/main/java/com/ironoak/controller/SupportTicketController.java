package com.ironoak.controller;

import com.ironoak.dto.request.SupportTicketFilter;
import com.ironoak.domain.enums.TicketStatus;
import com.ironoak.dto.request.CreateSupportTicketRequest;
import com.ironoak.dto.request.UpdateSupportTicketStatusRequest;
import com.ironoak.dto.response.SupportTicketResponse;
import com.ironoak.services.SupportTicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raising a ticket is public (Piper escalates); working the queue is
 * staff-only.
 */
@RestController
public class SupportTicketController {

    private final SupportTicketService tickets;

    public SupportTicketController(SupportTicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping("/api/support-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public SupportTicketResponse raise(@Valid @RequestBody CreateSupportTicketRequest request) {

        return tickets.create(request);
    }

    @GetMapping("/api/admin/support-tickets")
    public PagedModel<SupportTicketResponse> list(
            SupportTicketFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return new PagedModel<>(tickets.list(filter, pageable));
    }

    @GetMapping("/api/admin/support-tickets/{id}")
    public SupportTicketResponse get(@PathVariable Long id) {

        return tickets.get(id);
    }

    @PatchMapping("/api/admin/support-tickets/{id}/status")
    public SupportTicketResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateSupportTicketStatusRequest request) {

        return tickets.updateStatus(id, request.status());
    }
}
