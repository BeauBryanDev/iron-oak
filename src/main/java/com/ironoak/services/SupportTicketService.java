package com.ironoak.services;

import org.springframework.data.jpa.domain.Specification;
import com.ironoak.dto.request.SupportTicketFilter;
import com.ironoak.domain.ChatSession;
import com.ironoak.domain.Customer;
import com.ironoak.domain.SupportTicket;
import com.ironoak.domain.enums.TicketStatus;
import com.ironoak.dto.request.CreateSupportTicketRequest;
import com.ironoak.dto.response.SupportTicketResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.SupportTicketMapper;
import com.ironoak.repository.ChatSessionRepository;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.domain.CustomerOrder;
import com.ironoak.repository.SupportTicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * Escalations from Piper to a person. Tickets are created publicly and worked
 * from /api/admin.
 */
@Service
@Transactional
public class SupportTicketService {

        private static final Map<TicketStatus, Set<TicketStatus>> TRANSITIONS = Map.of(
                        TicketStatus.OPEN, Set.of(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED),
                        TicketStatus.IN_PROGRESS, Set.of(TicketStatus.CLOSED),
                        TicketStatus.CLOSED, Set.of());

        private final SupportTicketRepository tickets;
        private final ChatSessionRepository chatSessions;
        private final CustomerService customers;
        private final SupportTicketMapper mapper;
        private final CustomerOrderRepository orders;

        public SupportTicketService(SupportTicketRepository tickets,
                        ChatSessionRepository chatSessions,
                        CustomerService customers,
                        SupportTicketMapper mapper,
                        CustomerOrderRepository orders) {

                this.tickets = tickets;
                this.chatSessions = chatSessions;
                this.customers = customers;
                this.mapper = mapper;
                this.orders = orders;
        }

        /**
         * Piper's escalation. When the customer gave no email but the ticket is about
         * an order, the order's email (and so its customer) goes on the ticket. The
         * email stays on the server; Piper never sees it.
         */
        public SupportTicketResponse createFromChat(CreateSupportTicketRequest request,
                        String orderNumber) {

                if (request.customerEmail() != null && !request.customerEmail().isBlank()) {
                        return create(request);
                }
                String orderEmail = orderNumber == null || orderNumber.isBlank()
                                ? null
                                : orders.findWithItemsByOrderNumber(OrderService.normalizeOrderNumber(orderNumber))
                                                .map(CustomerOrder::getCustomerEmail)
                                                .orElse(null);

                return create(new CreateSupportTicketRequest(orderEmail,
                                request.chatSessionId(),
                                request.reason(),
                                request.summary()));
        }

        /**
         * Links the ticket to the customer by email when one matches, otherwise to the
         * chat
         * session's customer if it has one. A ticket from a total guest is still
         * accepted.
         */
        public SupportTicketResponse create(CreateSupportTicketRequest request) {

                ChatSession session = request.chatSessionId() == null ? null
                                : chatSessions.findById(request.chatSessionId())
                                                .orElseThrow(() -> new ResourceNotFoundException("Chat session",
                                                                request.chatSessionId()));

                Customer customer = customers.findByEmail(request.customerEmail())
                                .orElse(session == null ? null : session.getCustomer());

                SupportTicket ticket = new SupportTicket(customer,
                                CustomerService.normalizeEmail(request.customerEmail()),
                                session, request.reason().trim(),
                                request.summary().trim());

                return mapper.toResponse(tickets.saveAndFlush(ticket));
        }

        @Transactional(readOnly = true)
        public SupportTicketResponse get(Long id) {

                return mapper.toResponse(tickets.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Support ticket", id)));
        }

        @Transactional(readOnly = true)
        public Page<SupportTicketResponse> list(SupportTicketFilter filter, Pageable pageable) {

                Specification<SupportTicket> spec = Specification.allOf(

                                FilterSpecs.in("status", filter.status()),
                                FilterSpecs.dateRange("createdAt", filter.from(), filter.to()),
                                !FilterSpecs.hasText(filter.q()) ? null
                                                : (root, query, cb) -> FilterSpecs.anyContains(cb, filter.q(),
                                                                root.<String>get("customerEmail"),
                                                                root.<String>get("reason"),
                                                                root.<String>get("summary")));

                return tickets.findAll(spec, pageable).map(mapper::toResponse);
        }

        public SupportTicketResponse updateStatus(Long id, TicketStatus newStatus) {

                SupportTicket ticket = tickets.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Support ticket", id));

                if (!TRANSITIONS.get(ticket.getStatus()).contains(newStatus)) {

                        throw new BusinessRuleException(
                                        "Cannot change ticket " + id + " from " + ticket.getStatus() + " to "
                                                        + newStatus);
                }
                ticket.setStatus(newStatus);

                return mapper.toResponse(tickets.saveAndFlush(ticket));
        }
}
