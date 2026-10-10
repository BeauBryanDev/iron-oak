package com.ironoak.services;

import com.ironoak.domain.enums.AuditAction;
import com.ironoak.repository.ChatSessionRepository;
import com.ironoak.repository.SupportTicketRepository;
import com.ironoak.repository.WarrantyClaimRepository;
import com.ironoak.repository.ServiceBookingRepository;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.exceptions.ResourceInUseException;
import com.ironoak.exceptions.DuplicateResourceException;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.dto.request.PatchCustomerRequest;
import com.ironoak.dto.request.CustomerRequest;
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

/**
 * Staff management of guest customers. Unlike checkout, which reuses the
 * customer with a
 * matching email, creating here rejects a duplicate email. Deleting is allowed
 * only for a
 * customer nothing refers to.
 */
@Service
@Transactional
public class AdminCustomerService {

    private final CustomerRepository customers;
    private final CustomerOrderRepository orders;
    private final ServiceBookingRepository bookings;
    private final WarrantyClaimRepository claims;
    private final SupportTicketRepository tickets;
    private final ChatSessionRepository chatSessions;
    private final CustomerMapper mapper;
    private final AuditService audit;

    public AdminCustomerService(CustomerRepository customers,
            CustomerOrderRepository orders,
            ServiceBookingRepository bookings,
            WarrantyClaimRepository claims,
            SupportTicketRepository tickets,
            ChatSessionRepository chatSessions,
            CustomerMapper mapper,
            AuditService audit) {

        this.customers = customers;
        this.orders = orders;
        this.bookings = bookings;
        this.claims = claims;
        this.tickets = tickets;
        this.chatSessions = chatSessions;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(CustomerFilter filter, Pageable pageable) {

        Specification<Customer> spec = Specification.allOf(
                FilterSpecs.dateRange("createdAt", filter.from(),
                        filter.to()),
                !FilterSpecs.hasText(filter.q()) ? null
                        : (root, query, cb) -> FilterSpecs.anyContains(cb, filter.q(),
                                root.<String>get("name"), root.<String>get("email"),
                                root.<String>get("phone")));

        return customers.findAll(spec, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {

        return mapper.toResponse(find(id));
    }

    public CustomerResponse create(CustomerRequest request) {

        String email = CustomerService.normalizeEmail(request.email());
        requireFreeEmail(email, null);
        Customer customer = new Customer(request.name().trim(),
                email, blankToNull(request.phone()));
        customer.setAddress(blankToNull(request.address()));
        CustomerResponse created = mapper.toResponse(customers.saveAndFlush(customer));
        audit.record(AuditAction.CUSTOMER_CREATE,
                "CUSTOMER", created.id(), null, created);

        return created;
    }

    /**
     * Replaces every editable field; an omitted email, phone or address is cleared.
     */
    public CustomerResponse update(Long id, CustomerRequest request) {

        Customer customer = find(id);
        CustomerResponse before = mapper.toResponse(customer);
        String email = CustomerService.normalizeEmail(request.email());

        requireFreeEmail(email, id);
        customer.setName(request.name().trim());
        customer.setEmail(email);
        customer.setPhone(blankToNull(request.phone()));
        customer.setAddress(blankToNull(request.address()));
        CustomerResponse after = mapper.toResponse(customers.saveAndFlush(customer));
        audit.record(AuditAction.CUSTOMER_UPDATE,
                "CUSTOMER", id, before, after);

        return after;
    }

    /** Changes only the fields that are present. */
    public CustomerResponse patch(Long id, PatchCustomerRequest request) {

        Customer customer = find(id);
        CustomerResponse before = mapper.toResponse(customer);

        if (request.name() != null) {

            if (request.name().isBlank()) {
                throw new BusinessRuleException("name must not be blank");
            }
            customer.setName(request.name().trim());
        }
        if (request.email() != null) {

            String email = CustomerService.normalizeEmail(request.email());
            requireFreeEmail(email, id);
            customer.setEmail(email);
        }
        if (request.phone() != null) {

            customer.setPhone(blankToNull(request.phone()));
        }
        if (request.address() != null) {

            customer.setAddress(blankToNull(request.address()));
        }
        CustomerResponse after = mapper.toResponse(customers.saveAndFlush(customer));
        audit.record(AuditAction.CUSTOMER_UPDATE,
                "CUSTOMER", id, before, after);

        return after;
    }

    /**
     * Hard delete, refused while orders, bookings, claims, tickets or chats still
     * point at the customer.
     */
    public void delete(Long id) {

        Customer customer = find(id);
        if (orders.existsByCustomerId(id) || bookings.existsByCustomerId(id) || claims.existsByCustomerId(id)
                || tickets.existsByCustomerId(id) || chatSessions.existsByCustomerId(id)) {

            throw new ResourceInUseException("Customer " + id
                    + " has orders, bookings, claims, tickets or chats and cannot be deleted");
        }
        CustomerResponse before = mapper.toResponse(customer);
        customers.delete(customer);
        customers.flush();
        audit.record(AuditAction.CUSTOMER_DELETE,
                "CUSTOMER", id, before, null);
    }

    private Customer find(Long id) {

        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    private void requireFreeEmail(String email, Long ownId) {

        if (email == null) {
            return;
        }
        customers.findFirstByEmailIgnoreCase(email)
                .filter(other -> !other.getId().equals(ownId))
                .ifPresent(other -> {

                    throw new DuplicateResourceException("Customer", "email", email);
                });
    }

    private static String blankToNull(String value) {

        return value == null || value.isBlank() ? null : value.trim();
    }
}
