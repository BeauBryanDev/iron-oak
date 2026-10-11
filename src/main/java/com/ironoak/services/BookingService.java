package com.ironoak.services;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import com.ironoak.dto.request.BookingFilter;
import com.ironoak.config.ShippingProperties;
import com.ironoak.domain.Customer;
import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.enums.AuditAction;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.domain.enums.PricingType;
import com.ironoak.dto.request.ConfirmBookingRequest;
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

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Technician visits (Bogota and Medellin only). Flow: the customer books
 * (REQUESTED); staff
 * confirm it after checking a technician is available on site, which prices the
 * visit and creates
 * a PENDING_PAYMENT order (confirm); the customer pays by order number; staff
 * complete the visit
 * once it is paid. Customers act on their own bookings by proving the email on
 * the booking; staff
 * methods (no email) are only reachable from /api/admin routes.
 */
@Service
@Transactional
public class BookingService {

    private static final Set<BookingStatus> OPEN = EnumSet.of(BookingStatus.REQUESTED,
            BookingStatus.CONFIRMED);

    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = Map.of(
            BookingStatus.REQUESTED,
            Set.of(BookingStatus.CONFIRMED,
                    BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED,
            Set.of(BookingStatus.COMPLETED,
                    BookingStatus.CANCELLED),
            BookingStatus.COMPLETED,
            Set.of(),
            BookingStatus.CANCELLED,
            Set.of());

    private final ServiceBookingRepository bookings;
    private final ServiceOfferingRepository serviceOfferings;
    private final CustomerService customers;
    private final BookingMapper mapper;
    private final OrderService orders;
    private final ShippingProperties shippingProperties;
    private final AuditService audit;

    /** Order states that mean the visit has been paid for. */
    private static final Set<OrderStatus> PAID = EnumSet.of(OrderStatus.CONFIRMED,
            OrderStatus.IN_PROGRESS,
            OrderStatus.COMPLETED);

    /**
     * Order states that can still be paid, so cancelling the visit cancels the
     * order too.
     */
    private static final Set<OrderStatus> UNPAID = EnumSet.of(OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED);

    public BookingService(ServiceBookingRepository bookings,
            ServiceOfferingRepository serviceOfferings,
            CustomerService customers,
            BookingMapper mapper,
            OrderService orders,
            ShippingProperties shippingProperties,
            AuditService audit) {

        this.bookings = bookings;
        this.serviceOfferings = serviceOfferings;
        this.customers = customers;
        this.mapper = mapper;
        this.orders = orders;
        this.shippingProperties = shippingProperties;
        this.audit = audit;
    }

    public BookingResponse create(CreateBookingRequest request) {

        ServiceOffering service = serviceOfferings.findById(request.serviceOfferingId())
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Service",
                        request.serviceOfferingId()));

        ServiceArea.require(request.country(), request.city()); // technicians only in Bogota and Medellin

        Customer customer = customers.findOrCreate(request.customerName(),
                request.customerEmail(),
                request.customerPhone());

        ServiceBooking booking = new ServiceBooking(customer, service, request.locationAddress().trim(),
                request.scheduledAt(),
                request.machineModel(),
                request.notes());

        booking.setCountry(request.country().trim().toUpperCase(java.util.Locale.ROOT));
        booking.setCity(request.city().trim());

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

    public BookingResponse reschedule(Long id, String customerEmail,
            RescheduleBookingRequest request) {

        ServiceBooking booking = owned(id, customerEmail);
        requireOpen(booking, "rescheduled");
        if (booking.getStatus() != BookingStatus.REQUESTED) {
            // the technician was booked for that date: changing it needs staff
            throw new BusinessRuleException("Booking " + id + " is already confirmed; contact us to change the date");
        }
        booking.reschedule(request.scheduledAt());

        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    public BookingResponse cancel(Long id, String customerEmail,
            CancelBookingRequest request) {

        ServiceBooking booking = owned(id, customerEmail);
        requireOpen(booking, "cancelled");
        CustomerOrder order = booking.getOrder();

        if (order != null && PAID.contains(order.getStatus())) {
            throw new BusinessRuleException(
                    "Booking " + id + " is already paid; contact us to cancel it and get a refund");
        }
        cancelUnpaidOrder(booking);
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
    public Page<BookingResponse> queue(BookingFilter filter,
            Pageable pageable) {

        Collection<BookingStatus> statuses = filter.status() == null || filter.status().isEmpty() ? OPEN
                : filter.status();

        Specification<ServiceBooking> spec = Specification.allOf(
                FilterSpecs.in("status", statuses),
                FilterSpecs.dateRange("scheduledAt",
                        filter.from(), filter.to()),
                filter.serviceId() == null ? null
                        : (root, query, cb) -> cb.equal(root.get("serviceOffering").get("id"),
                                filter.serviceId()),
                filter.categoryId() == null ? null
                        : (root, query, cb) -> cb.equal(
                                root.get("serviceOffering").get("category").get("id"),
                                filter.categoryId()),
                matching(filter.q()));

        return bookings.findAll(spec, pageable).map(mapper::toResponse);
    }

    private static Specification<ServiceBooking> matching(String search) {

        if (!FilterSpecs.hasText(search)) {
            return null;
        }
        return (root, query, cb) -> {

            Join<ServiceBooking, Customer> customer = root.join("customer", JoinType.LEFT);

            return FilterSpecs.anyContains(cb, search, customer.<String>get("name"),
                    customer.<String>get("email"),
                    root.<String>get("locationAddress"),
                    root.<String>get("machineModel"));
        };
    }

    public BookingResponse updateStatus(Long id, BookingStatus newStatus) {

        ServiceBooking booking = bookings.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));

        if (!TRANSITIONS.get(booking.getStatus()).contains(newStatus)) {

            throw new BusinessRuleException(
                    "Cannot change booking " + id + " from " + booking.getStatus() + " to " + newStatus);
        }
        if (newStatus == BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Confirm a visit with POST /api/admin/bookings/" + id
                    + "/confirm, which checks the price and creates the order to pay");
        }
        CustomerOrder order = booking.getOrder();
        if (newStatus == BookingStatus.COMPLETED && order != null && !PAID.contains(order.getStatus())) {
            throw new BusinessRuleException("Booking " + id + " cannot be completed: its order "
                    + order.getOrderNumber() + " is " + order.getStatus() + ", not paid");
        }
        BookingStatus oldStatus = booking.getStatus();
        if (newStatus == BookingStatus.CANCELLED) {
            cancelUnpaidOrder(booking); // a paid order stays: staff refund it separately
            booking.cancel("Cancelled by staff");

        } else {
            booking.setStatus(newStatus);
        }
        BookingResponse updated = mapper.toResponse(bookings.saveAndFlush(booking));
        audit.record(AuditAction.BOOKING_STATUS, "BOOKING", id, Map.of("status", oldStatus),
                Map.of("status", newStatus));
        return updated;
    }

    /**
     * Staff confirm the visit after checking a technician is available that day.
     * Prices it like an
     * order line (FIXED listed price, HOURLY rate x estimatedHours, QUOTE the staff
     * price) and
     * creates a PENDING_PAYMENT order the customer pays by order number. The order
     * is held for
     * quote-hold-hours (72 h), but never past the visit itself.
     */
    public BookingResponse confirm(Long id, ConfirmBookingRequest request) {

        ServiceBooking booking = bookings.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", id));
        if (booking.getStatus() != BookingStatus.REQUESTED) {
            throw new BusinessRuleException(
                    "Booking " + id + " is " + booking.getStatus() + " and cannot be confirmed");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!booking.getScheduledAt().isAfter(now)) {
            throw new BusinessRuleException("The visit date of booking " + id
                    + " has passed; the customer must reschedule it first");
        }
        ServiceOffering service = booking.getServiceOffering();
        if (request.price() != null && service.getPricingType() != PricingType.QUOTE) {
            throw new BusinessRuleException(
                    service.getName() + " has a listed price; send price only for quote-only services");
        }
        OffsetDateTime holdUntil = now.plusHours(shippingProperties.getQuoteHoldHours());
        if (holdUntil.isAfter(booking.getScheduledAt())) {
            holdUntil = booking.getScheduledAt();
        }
        CustomerOrder order = orders.createForBooking(booking,
                orders.priceServiceLine(service, 1, request.estimatedHours(),
                        request.price()),
                holdUntil);

        booking.setOrder(order);
        booking.setStatus(BookingStatus.CONFIRMED);

        BookingResponse confirmed = mapper.toResponse(bookings.saveAndFlush(booking));
        audit.record(AuditAction.BOOKING_CONFIRMED, "BOOKING", id,
                Map.of("status", BookingStatus.REQUESTED),
                Map.of("status", BookingStatus.CONFIRMED, "orderNumber",
                        order.getOrderNumber(),
                        "grandTotal", order.getGrandTotal()));

        return confirmed;
    }

    /** Cancels the visit's order while it is still unpaid (no money to return). */
    private void cancelUnpaidOrder(ServiceBooking booking) {

        CustomerOrder order = booking.getOrder();

        if (order != null && UNPAID.contains(order.getStatus())) {

            orders.updateStatus(order.getId(), OrderStatus.CANCELLED);
        }
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
