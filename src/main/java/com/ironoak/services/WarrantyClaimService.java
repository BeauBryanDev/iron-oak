package com.ironoak.services;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import com.ironoak.domain.Customer;
import org.springframework.data.jpa.domain.Specification;
import com.ironoak.dto.request.WarrantyClaimFilter;
import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.OrderItem;
import com.ironoak.domain.WarrantyClaim;
import com.ironoak.domain.enums.ClaimStatus;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.dto.request.CreateWarrantyClaimRequest;
import com.ironoak.dto.request.UpdateWarrantyClaimStatusRequest;
import com.ironoak.dto.response.WarrantyClaimResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.WarrantyClaimMapper;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.OrderItemRepository;
import com.ironoak.repository.WarrantyClaimRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class WarrantyClaimService {

    private static final List<ClaimStatus> ACTIVE = List.of(ClaimStatus.OPEN, ClaimStatus.IN_REVIEW);

    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITIONS = Map.of(
            ClaimStatus.OPEN, Set.of(ClaimStatus.IN_REVIEW, ClaimStatus.REJECTED),
            ClaimStatus.IN_REVIEW, Set.of(ClaimStatus.APPROVED, ClaimStatus.REJECTED),
            ClaimStatus.APPROVED, Set.of(ClaimStatus.RESOLVED),
            ClaimStatus.REJECTED, Set.of(),
            ClaimStatus.RESOLVED, Set.of());

    // Moving into these needs a note explaining the decision.
    private static final Set<ClaimStatus> NEEDS_NOTE = Set.of(ClaimStatus.APPROVED,
            ClaimStatus.REJECTED, ClaimStatus.RESOLVED);

    private final WarrantyClaimRepository claims;
    private final CustomerOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CustomerService customers;
    private final WarrantyClaimMapper mapper;

    public WarrantyClaimService(WarrantyClaimRepository claims,
            CustomerOrderRepository orders,
            OrderItemRepository orderItems,
            CustomerService customers,
            WarrantyClaimMapper mapper) {
        this.claims = claims;
        this.orders = orders;
        this.orderItems = orderItems;
        this.customers = customers;
        this.mapper = mapper;
    }

    /**
     * A claim is accepted only for a product or machine line on a completed order
     * that
     * belongs to the customer, inside its warranty period, with no active claim
     * already.
     * The warranty runs from the order date: the schema does not record a delivery
     * date.
     */
    public WarrantyClaimResponse create(CreateWarrantyClaimRequest request) {

        CustomerOrder order = orders.findById(request.orderId())
                .filter(o -> CustomerService.hasEmail(o.getCustomer(), request.customerEmail()))
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.orderId()));

        OrderItem item = orderItems.findById(request.orderItemId())
                .filter(i -> i.getOrder().getId().equals(order.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order item", request.orderItemId()));

        if (order.getStatus() != OrderStatus.COMPLETED) {

            throw new BusinessRuleException("Warranty claims can only be made on completed orders");
        }
        int warrantyMonths = warrantyMonths(item);

        if (warrantyMonths <= 0) {

            throw new BusinessRuleException("This item has no warranty");
        }
        if (order.getCreatedAt().plusMonths(warrantyMonths).isBefore(OffsetDateTime.now())) {
            throw new BusinessRuleException("The " + warrantyMonths + "-month warranty on this item has expired");
        }
        if (claims.existsByOrderItemIdAndStatusIn(item.getId(), ACTIVE)) {
            throw new BusinessRuleException("There is already an open claim for this item");
        }

        WarrantyClaim claim = new WarrantyClaim(order.getCustomer(),
                order, item.getId(), request.description().trim());

        return mapper.toResponse(claims.saveAndFlush(claim));
    }

    @Transactional(readOnly = true)
    public WarrantyClaimResponse get(Long id, String customerEmail) {

        WarrantyClaim claim = claims.findById(id)
                .filter(c -> CustomerService.hasEmail(c.getCustomer(), customerEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Warranty claim", id));

        return mapper.toResponse(claim);
    }

    @Transactional(readOnly = true)
    public List<WarrantyClaimResponse> listMine(String customerEmail) {
        return customers.findByEmail(customerEmail)
                .map(c -> mapper.toResponses(claims.findByCustomerIdOrderByCreatedAtDesc(c.getId())))
                .orElse(List.of());
    }

    // Staff side

    @Transactional(readOnly = true)
    public WarrantyClaimResponse getForStaff(Long id) {

        return mapper.toResponse(claims.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warranty claim", id)));
    }

    @Transactional(readOnly = true)
    public Page<WarrantyClaimResponse> list(WarrantyClaimFilter filter, Pageable pageable) {

        Specification<WarrantyClaim> spec = Specification.allOf(
                FilterSpecs.in("status", filter.status()),
                FilterSpecs.dateRange("createdAt", filter.from(), filter.to()),
                matching(filter.q()));

        return claims.findAll(spec, pageable).map(mapper::toResponse);
    }

    private static Specification<WarrantyClaim> matching(String search) {

        if (!FilterSpecs.hasText(search)) {
            return null;
        }
        return (root, query, cb) -> {

            Join<WarrantyClaim, Customer> customer = root.join("customer", JoinType.LEFT);
            Predicate text = FilterSpecs.anyContains(cb, search, customer.<String>get("name"),
                    customer.<String>get("email"), root.<String>get("description"));
            String trimmed = search.trim();

            return trimmed.matches("\\d{1,18}")
                    ? cb.or(text, cb.equal(root.get("order").get("id"),
                            Long.parseLong(trimmed)))
                    : text;
        };
    }

    public WarrantyClaimResponse updateStatus(Long id,
            UpdateWarrantyClaimStatusRequest request) {

        WarrantyClaim claim = claims.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warranty claim", id));

        ClaimStatus next = request.status();
        if (!TRANSITIONS.get(claim.getStatus()).contains(next)) {

            throw new BusinessRuleException("Cannot change claim " + id + " from " + claim.getStatus() + " to " + next);
        }
        String note = request.resolutionNote() == null ? null : request.resolutionNote().trim();

        if (NEEDS_NOTE.contains(next) && (note == null || note.isEmpty())) {

            throw new BusinessRuleException("A resolution note is required for " + next);
        }
        if (next == ClaimStatus.REJECTED || next == ClaimStatus.RESOLVED) {
            claim.resolve(next, note);

        } else {
            claim.review(next, note);
        }
        return mapper.toResponse(claims.saveAndFlush(claim));
    }

    private int warrantyMonths(OrderItem item) {

        if (item.getItemType() == OrderItemType.PRODUCT) {

            return item.getProduct().getWarrantyMonths();
        }
        if (item.getItemType() == OrderItemType.MACHINE) {

            return item.getMillingMachine().getWarrantyMonths();

        }
        throw new BusinessRuleException("Services are not covered by the warranty claim process");
    }
}
