package com.ironoak.mapper;

import com.ironoak.domain.SupportTicket;
import com.ironoak.dto.response.SupportTicketResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/** Customer and chat session are optional on a ticket; call inside a transaction. */
@Component
public class SupportTicketMapper {

    public SupportTicketResponse toResponse(SupportTicket ticket) {
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getStatus(),
                ticket.getCustomer() == null ? null : ticket.getCustomer().getName(),
                ticket.getCustomerEmail(),
                ticket.getChatSession() == null ? null : ticket.getChatSession().getId(),
                ticket.getReason(),
                ticket.getSummary(),
                ticket.getCreatedAt());
    }

    public List<SupportTicketResponse> toResponses(List<SupportTicket> tickets) {
        return tickets.stream().map(this::toResponse).toList();
    }
}
