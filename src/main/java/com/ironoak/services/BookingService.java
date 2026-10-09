package com.ironoak.services;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import com.ironoak.dto.request.BookingFilter;
import com.ironoak.domain.Customer;
import com.ironoak.domain.ServiceBooking;
import com.ironoak.domain.ServiceOffering;
import com.ironoak.domain.enums.BookingStatus;
import com.ironoak.dto.request.CancelBookingRequest;
import com.ironoak.dto.request.CreateBookingRequest;
import com.ironoak.dto.request.RescheduleBookingRequest;
import com.ironoak.dto.response.BookingResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.BookingMapper;
import com.ironoak.repository.ServiceBookingRepository;
import com.ironoak.repository.ServiceOfferingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Technician visits. Customers act on their own bookings by proving the email
 * on the
 * booking; staff methods (no email) are only reachable from /api/admin routes.
 */
@Service
@Transactional
public class BookingService {

    private static final Set<BookingStatus> OPEN = EnumSet.of(BookingStatus.REQUESTED, BookingStatus.CONFIRMED);

    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = Map.of(
            BookingStatus.REQUESTED, Set.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED, Set.of(BookingStatus.COMPLETED, BookingStatus.CANCELLED),
            BookingStatus.COMPLETED, Set.of(),
            BookingStatus.CANCELLED, Set.of());

    private final ServiceBookingRepository bookings;
    private final ServiceOfferingRepository serviceOfferings;
    private final CustomerService customers;
    private final BookingMapper mapper;

    public BookingService(ServiceBookingRepository bookings,
            ServiceOfferingRepository serviceOfferings,
            CustomerService customers,
            BookingMapper mapper) {
        this.bookings = bookings;
        this.serviceOfferings = serviceOfferings;
        this.customers = customers;
        this.mapper = mapper;
    }

    public BookingResponse create(CreateBookingRequest request) {
        ServiceOffering service = serviceOfferings.findById(request.serviceOfferingId())
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Service", request.serviceOfferingId()));
        Customer customer = customers.findOrCreate(request.customerName(), request.customerEmail(),
                request.customerPhone());
        ServiceBooking booking = new ServiceBooking(customer, service, request.locationAddress().trim(),
                request.scheduledAt(), request.machineModel(), request.notes());
        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id, String customerEmail) {
        return mapper.toResponse(owned(id, customerEmail));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listMine(String customerEmail) {
        return customers.findByEmail(customerEmail)
                .map(c -> mapper.toResponses(bookings.findByCustomerIdOrderByScheduledAtDesc(c.getId())))
                .orElse(List.of());
    }

    public BookingResponse reschedule(Long id, String customerEmail, RescheduleBookingRequest request) {
        ServiceBooking booking = owned(id, customerEmail);
        requireOpen(booking, "rescheduled");
        booking.reschedule(request.scheduledAt());
        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    public BookingResponse cancel(Long id, String customerEmail, CancelBookingRequest request) {
        ServiceBooking booking = owned(id, customerEmail);
        requireOpen(booking, "cancelled");
        booking.cancel(request == null ? null : request.reason());
        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    // Staff side

    @Transactional(readOnly = true)
    public BookingResponse getForStaff(Long id) {
        return mapper.toResponse(bookings.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id)));
    }

    /** The technician queue. With no statuses given, shows the open ones. */
    @Transactional(readOnly = true)
    public Page<BookingResponse> queue(BookingFilter filter, Pageable pageable) {
        Collection<BookingStatus> statuses = filter.status() == null || filter.status().isEmpty() ? OPEN : filter.status();
        Specification<ServiceBooking> spec = Specification.allOf(
                FilterSpecs.in("status", statuses),
                FilterSpecs.dateRange("scheduledAt", filter.from(), filter.to()),
                filter.serviceId() == null ? null
                        : (root, query, cb) -> cb.equal(root.get("serviceOffering").get("id"), filter.serviceId()),
                filter.categoryId() == null ? null
                        : (root, query, cb) -> cb.equal(
                                root.get("serviceOffering").get("category").get("id"), filter.categoryId()),
                matching(filter.q()));
        return bookings.findAll(spec, pageable).map(mapper::toResponse);
    }

    private static Specification<ServiceBooking> matching(String search) {
        if (!FilterSpecs.hasText(search)) {
            return null;
        }
        return (root, query, cb) -> {
            Join<ServiceBooking, Customer> customer = root.join("customer", JoinType.LEFT);
            return FilterSpecs.anyContains(cb, search, customer.<String>get("name"), customer.<String>get("email"),
                    root.<String>get("locationAddress"), root.<String>get("machineModel"));
        };
    }

    public BookingResponse updateStatus(Long id, BookingStatus newStatus) {
        ServiceBooking booking = bookings.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        if (!TRANSITIONS.get(booking.getStatus()).contains(newStatus)) {
            throw new BusinessRuleException(
                    "Cannot change booking " + id + " from " + booking.getStatus() + " to " + newStatus);
        }
        if (newStatus == BookingStatus.CANCELLED) {
            booking.cancel("Cancelled by staff");
        } else {
            booking.setStatus(newStatus);
        }
        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    /**
     * A booking that exists but belongs to someone else looks the same as one that
     * does not exist.
     */
    private ServiceBooking owned(Long id, String customerEmail) {
        ServiceBooking booking = bookings.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));

        if (!CustomerService.hasEmail(booking.getCustomer(), customerEmail)) {
            throw new ResourceNotFoundException("Booking", id);
        }
        return booking;
    }

    private void requireOpen(ServiceBooking booking, String action) {
        if (!OPEN.contains(booking.getStatus())) {
            throw new BusinessRuleException(
                    "Booking " + booking.getId() + " is " + booking.getStatus() + " and cannot be " + action);
        }
    }
}
