package com.ironoak.services;

import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.ServiceOffering;
import com.ironoak.domain.ServiceQuote;
import com.ironoak.domain.enums.AuditAction;
import com.ironoak.domain.enums.PricingType;
import com.ironoak.domain.enums.ServiceQuoteStatus;
import com.ironoak.dto.request.CreateServiceQuoteRequest;
import com.ironoak.dto.request.ServiceQuoteFilter;
import com.ironoak.dto.response.ServiceQuoteResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.repository.ServiceOfferingRepository;
import com.ironoak.repository.ServiceQuoteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Quote requests for QUOTE-priced services, which cannot be ordered directly.
 * The customer
 * describes the job; staff either price it, which creates a PENDING_PAYMENT
 * order (one service
 * line at that price, plus tax) that the customer pays by order number like a
 * Piper order, or
 * decline it with a note. Customers read their request back with its id and
 * email.
 */
@Service
@Transactional
public class ServiceQuoteService {

    private final ServiceQuoteRepository quotes;
    private final ServiceOfferingRepository services;
    private final CustomerService customers;
    private final OrderService orders;
    private final AuditService audit;

    public ServiceQuoteService(ServiceQuoteRepository quotes,
            ServiceOfferingRepository services,
            CustomerService customers,
            OrderService orders,
            AuditService audit) {

        this.quotes = quotes;
        this.services = services;
        this.customers = customers;
        this.orders = orders;
        this.audit = audit;
    }

    public ServiceQuoteResponse request(CreateServiceQuoteRequest request) {

        ServiceOffering service = services.findByCode(request.serviceCode().trim())
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Service", request.serviceCode()));

        if (service.getPricingType() != PricingType.QUOTE) {
            throw new BusinessRuleException(service.getName() + " has a listed price; order it directly");
        }
        ServiceArea.require(request.country(),
                request.city()); // technicians only in Bogota and Medellin
        String name = request.customerName().trim();
        String phone = AdminProductService.blankToNull(request.customerPhone());

        ServiceQuote quote = new ServiceQuote(service,
                customers.findOrCreate(name,
                        request.customerEmail(),
                        phone),
                name,
                request.customerEmail(),
                phone,
                request.country(),
                AdminProductService.blankToNull(request.city()),
                request.description().trim());

        return ServiceQuoteResponse.from(quotes.saveAndFlush(quote));
    }

    /**
     * The customer's view; a wrong email is a 404, the same as a missing request.
     */
    @Transactional(readOnly = true)
    public ServiceQuoteResponse getForCustomer(Long id, String email) {

        ServiceQuote quote = find(id);

        String normalized = CustomerService.normalizeEmail(email);

        if (normalized == null || !normalized.equals(quote.getCustomerEmail())) {
            throw new ResourceNotFoundException("Service quote", id);
        }
        return ServiceQuoteResponse.from(quote);
    }

    @Transactional(readOnly = true)
    public Page<ServiceQuoteResponse> list(ServiceQuoteFilter filter, Pageable pageable) {

        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new BusinessRuleException("from must not be after to");
        }
        Specification<ServiceQuote> spec = Specification.allOf(
                FilterSpecs.in("status", filter.status()),
                FilterSpecs.dateRange("createdAt", filter.from(), filter.to()),
                !FilterSpecs.hasText(filter.q()) ? null
                        : (root, query, cb) -> FilterSpecs.anyContains(cb, filter.q(),
                                root.<String>get("customerName"), root.<String>get("customerEmail")));

        return quotes.findAll(spec, pageable).map(ServiceQuoteResponse::from);
    }

    @Transactional(readOnly = true)
    public ServiceQuoteResponse get(Long id) {
        return ServiceQuoteResponse.from(find(id));
    }

    /**
     * Staff price the job: creates the order the customer pays. Only once per
     * request.
     */
    public ServiceQuoteResponse price(Long id, BigDecimal price, String note) {

        ServiceQuote quote = lockRequested(id);
        BigDecimal amount = price.setScale(2, RoundingMode.HALF_UP);
        CustomerOrder order = orders.createForServiceQuote(quote, amount);

        quote.markQuoted(amount, AdminProductService.blankToNull(note), order);
        quotes.saveAndFlush(quote);

        audit.record(AuditAction.SERVICE_QUOTE_PRICED, "SERVICE_QUOTE", id,
                Map.of("status", ServiceQuoteStatus.REQUESTED),
                Map.of("status", quote.getStatus(),
                        "quotedPrice", amount,
                        "orderNumber", order.getOrderNumber(),
                        "grandTotal", order.getGrandTotal()));

        return ServiceQuoteResponse.from(quote);
    }

    public ServiceQuoteResponse decline(Long id, String note) {

        ServiceQuote quote = lockRequested(id);
        quote.decline(note.trim());
        quotes.saveAndFlush(quote);
        audit.record(AuditAction.SERVICE_QUOTE_DECLINED, "SERVICE_QUOTE", id,
                Map.of("status", ServiceQuoteStatus.REQUESTED),
                Map.of("status", quote.getStatus(), "staffNote",
                        quote.getStaffNote()));

        return ServiceQuoteResponse.from(quote);
    }

    private ServiceQuote lockRequested(Long id) {

        ServiceQuote quote = quotes.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service quote", id));

        if (quote.getStatus() != ServiceQuoteStatus.REQUESTED) {
            throw new BusinessRuleException("Service quote " + id + " is already " + quote.getStatus());
        }
        return quote;
    }

    private ServiceQuote find(Long id) {

        return quotes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service quote", id));
    }
}
