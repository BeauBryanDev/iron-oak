package com.ironoak.services;

import com.ironoak.domain.enums.AuditAction;
import com.ironoak.config.ShippingProperties;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import com.ironoak.dto.response.PublicOrderResponse;
import com.ironoak.domain.enums.ShippingStatus;
import com.ironoak.domain.enums.ShippingSource;
import com.ironoak.config.CheckoutProperties;
import com.ironoak.domain.Payment;
import com.ironoak.domain.enums.PaymentStatus;
import com.ironoak.dto.request.OrderFilter;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.EnumSet;
import com.ironoak.domain.Customer;
import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.MillingMachine;
import com.ironoak.domain.OrderItem;
import com.ironoak.domain.Product;
import com.ironoak.domain.ServiceOffering;
import com.ironoak.domain.ServiceQuote;
import com.ironoak.domain.enums.ShippingMode;
import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.domain.enums.OrderStatus;
import com.ironoak.dto.request.CreateOrderRequest;
import com.ironoak.dto.response.OrderResponse;
import com.ironoak.exceptions.InvalidOrderException;
import com.ironoak.exceptions.OutOfStockException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.OrderMapper;
import com.ironoak.repository.CustomerOrderRepository;
import com.ironoak.repository.MillingMachineRepository;
import com.ironoak.repository.ProductRepository;
import com.ironoak.repository.ServiceOfferingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle. A web or Piper order is created PENDING_PAYMENT: it prices
 * every line from
 * the catalog, takes product stock at once and holds it until it is paid or the
 * reservation
 * runs out. Payment moves it to CONFIRMED; an unpaid order
 * EXPIRES andreturns its stock. Staff-entered orders start CONFIRMED.
 */
@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /**
     * Status changes staff may make by hand. Payment-driven moves go through the
     * methods below.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(

            OrderStatus.DRAFT,
            Set.of(OrderStatus.CONFIRMED,
                    OrderStatus.CANCELLED),
            OrderStatus.PENDING_PAYMENT,
            Set.of(OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_FAILED,
            Set.of(OrderStatus.CANCELLED),
            OrderStatus.EXPIRED,
            Set.of(),
            OrderStatus.CONFIRMED,
            Set.of(OrderStatus.IN_PROGRESS,
                    OrderStatus.CANCELLED),
            OrderStatus.IN_PROGRESS, Set.of(OrderStatus.COMPLETED,
                    OrderStatus.CANCELLED),
            OrderStatus.COMPLETED,
            Set.of(),
            OrderStatus.CANCELLED,
            Set.of());

    private static final Set<OrderStatus> AWAITING_PAYMENT = EnumSet.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAYMENT_FAILED);

    private final CustomerOrderRepository orders;
    private final CustomerService customers;
    private final ProductRepository products;
    private final ServiceOfferingRepository serviceOfferings;
    private final MillingMachineRepository machines;
    private final PaymentRepository payments;
    private final OrderMapper mapper;
    private final ShippingService shipping;
    private final CheckoutProperties checkout;
    private final ShippingProperties shippingProperties;
    private final TaxService taxes;
    private final AuditService audit;
    private final TransactionTemplate transactions;

    public OrderService(CustomerOrderRepository orders,
            CustomerService customers,
            ProductRepository products,
            ServiceOfferingRepository serviceOfferings,
            MillingMachineRepository machines,
            PaymentRepository payments,
            OrderMapper mapper,
            ShippingService shipping,
            CheckoutProperties checkout,
            ShippingProperties shippingProperties,
            TaxService taxes,
            AuditService audit,
            PlatformTransactionManager transactionManager) {

        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.serviceOfferings = serviceOfferings;
        this.machines = machines;
        this.payments = payments;
        this.mapper = mapper;
        this.shipping = shipping;
        this.checkout = checkout;
        this.shippingProperties = shippingProperties;
        this.taxes = taxes;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public OrderResponse create(CreateOrderRequest request,
            OrderChannel channel) {

        return create(request, channel, null);
    }

    /**
     * Prices every line from the catalog (the client never sends prices), takes
     * product stock
     * atomically, and saves the order. Safe to retry: a repeated idempotencyKey
     * returns the
     * order the first call created instead of taking stock a second time. Any
     * failure rolls the
     * whole order back, including stock already taken for earlier lines.
     */
    public OrderResponse create(CreateOrderRequest request,
            OrderChannel channel,
            String idempotencyKey) {

        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();

        if (key != null) {

            var existing = orders.findByIdempotencyKey(key);
            if (existing.isPresent()) {

                return mapper.toResponse(existing.get());
            }
        }

        List<OrderItem> items = new ArrayList<>();
        for (CreateOrderRequest.Item line : request.items()) {
            items.add(buildItem(line));
        }
        requireCheckoutDetails(request, channel, items);
        // Staff orders without an address ship nothing; everyone else is priced by the
        // shared
        // ShippingService. A real order may spend one Google lookup on a city not
        // stored yet.
        ShippingService.Quote shippingQuote = isBlank(request.country()) ? ShippingService.Quote.nothingToShip()
                : shipping.quote(request.country(),
                        request.city(),
                        request.province(), items, true);

        // decrementStock clears the persistence context, so it runs after all lookups.
        boolean takesStock = false;

        for (OrderItem item : items) {

            if (item.getItemType() == OrderItemType.PRODUCT) {
                takesStock = true;
                if (products.decrementStock(item.getProduct().getId(), item.getQuantity()) == 0) {

                    throw new OutOfStockException(item.getProduct().getSku(), item.getQuantity());
                }
            }
        }

        CustomerOrder order = new CustomerOrder(findOrCreateCustomer(request), channel);

        order.setContact(trimToNull(request.customerName()),
                request.customerEmail(), trimToNull(request.customerPhone()));

        order.setCountry(request.country() == null ? null : request.country().toUpperCase(Locale.ROOT));
        order.setProvince(trimToNull(request.province()));
        order.setCity(trimToNull(request.city()));
        order.setShippingAddress(trimToNull(request.shippingAddress()));
        order.setCurrency(checkout.getCurrency());
        order.setShippingCost(shippingQuote.cost());
        order.setShippingStatus(shippingQuote.status());
        order.setShippingMode(shippingQuote.mode());
        order.setShippingDistanceKm(shippingQuote.distanceKm());
        order.setShippingSource(shippingQuote.source());
        order.setIdempotencyKey(key);
        order.setStockReserved(takesStock);

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {

            order.setReservationExpiresAt(OffsetDateTime.now().plusMinutes(holdMinutes(channel, shippingQuote)));
        }
        items.forEach(order::addItem);
        // Items only, never shipping, and only above the threshold (TaxService).
        order.setTaxes(taxes.taxFor(order.getCountry(), order.getSubtotal()));
        order.recalculateTotals();

        OrderResponse created = mapper.toResponse(orders.save(order));
        if (channel == OrderChannel.ADMIN_MANUAL) {
            audit.record(AuditAction.ORDER_CREATE, "ORDER", created.id(), null, created);
        }
        return created;
    }

    /**
     * Staff priced a service quote request: one service line at that price, nothing to ship,
     * taxed like any order, PENDING_PAYMENT for quote-hold-hours so the customer can pay it by
     * order number. Called by ServiceQuoteService inside its transaction.
     */
    public CustomerOrder createForServiceQuote(ServiceQuote quote, BigDecimal price) {

        CustomerOrder order = new CustomerOrder(quote.getCustomer(), OrderChannel.WEB_CHECKOUT);
        order.setContact(quote.getCustomerName(), quote.getCustomerEmail(), quote.getCustomerPhone());
        order.setCountry(quote.getCountry());
        order.setCity(quote.getCity());
        order.setCurrency(checkout.getCurrency());
        order.setShippingMode(ShippingMode.NONE);
        order.setReservationExpiresAt(OffsetDateTime.now().plusHours(shippingProperties.getQuoteHoldHours()));
        order.addItem(OrderItem.ofService(quote.getServiceOffering(), 1, null, price));
        order.setTaxes(taxes.taxFor(order.getCountry(), order.getSubtotal()));
        order.recalculateTotals();
        return orders.saveAndFlush(order);
    }

    /**
     * How long a new unpaid order holds its stock: long enough for staff to quote
     * shipping, for a
     * Piper customer to come back with the order number, or for a web customer to
     * pay now.
     */
    private int holdMinutes(OrderChannel channel, ShippingService.Quote quote) {

        if (quote.status() == ShippingStatus.ON_REQUEST) {
            return shippingProperties.getQuoteHoldHours() * 60;
        }
        if (channel == OrderChannel.PIPER) {
            return shippingProperties.getPiperHoldMinutes();
        }
        return checkout.getReservationMinutes();
    }

    /**
     * Staff set the shipping of an order (normally one waiting for a quote).
     * Refused once money
     * has been received, because the total the customer paid must not change
     * afterwards.
     */
    public OrderResponse setShippingCost(Long id, BigDecimal cost) {

        CustomerOrder order = orders.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (!Set.of(OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.CONFIRMED,
                OrderStatus.DRAFT).contains(order.getStatus())) {

            throw new BusinessRuleException("Order " + order.getOrderNumber() + " is " + order.getStatus()
                    + "; its shipping can no longer change");
        }
        if (payments.sumAmountByOrderAndStatus(id, PaymentStatus.PAID).signum() > 0) {
            throw new BusinessRuleException("Order " + order.getOrderNumber() + " already has a payment");
        }
        Map<String, Object> before = Map.of("shippingCost", order.getShippingCost(),
                "shippingStatus", order.getShippingStatus(), "grandTotal", order.getGrandTotal());
        order.setShippingCost(cost.setScale(2, RoundingMode.HALF_UP));
        order.setShippingStatus(ShippingStatus.QUOTED);
        order.setShippingSource(ShippingSource.STAFF);
        order.recalculateTotals();
        orders.saveAndFlush(order);
        audit.record(AuditAction.ORDER_SHIPPING_QUOTE, "ORDER", id, before, Map.of("shippingCost",
                order.getShippingCost(), "shippingStatus", order.getShippingStatus(), "grandTotal",
                order.getGrandTotal()));

        return mapper.toResponse(orders.findWithItemsById(id).orElseThrow());
    }

    /**
     * The customer-safe view of an order, found by its order number (for the cart's
     * "pay my
     * order" field and for Piper). No name, email, phone or address is returned.
     */
    @Transactional(readOnly = true)
    public PublicOrderResponse getPublic(String orderNumber) {

        String number = orderNumber == null ? "" : orderNumber.trim().toUpperCase(Locale.ROOT);

        return orders.findWithItemsByOrderNumber(number)
                .map(mapper::toPublicResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order", number));
    }

    /**
     * Web and Piper orders need a contact to pay and to read the order back, and an
     * address for goods.
     */
    private void requireCheckoutDetails(CreateOrderRequest request,
            OrderChannel channel,
            List<OrderItem> items) {

        if (channel == OrderChannel.ADMIN_MANUAL) {
            return;
        }
        if (isBlank(request.customerName()) || isBlank(request.customerEmail())) {
            throw new InvalidOrderException("customerName and customerEmail are required");
        }
        boolean physical = items.stream().anyMatch(i -> i.getItemType() != OrderItemType.SERVICE);

        if (physical && (isBlank(request.country()) || isBlank(request.city()) || isBlank(request.shippingAddress()))) {
            throw new InvalidOrderException("country, city and shippingAddress are required for products and machines");
        }
        // technicians only work in Bogota and Medellin, Colombia
        if (items.stream().anyMatch(i -> i.getItemType() == OrderItemType.SERVICE)) {
            ServiceArea.require(request.country(), request.city());
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {

        return orders.findWithItemsById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    /**
     * For the customer: the order is only visible with the email it was placed
     * under.
     */
    @Transactional(readOnly = true)

    public OrderResponse getForCustomer(Long id, String customerEmail) {

        return orders.findWithItemsById(id)
                .filter(o -> ownedBy(o, customerEmail))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(OrderFilter filter, Pageable pageable) {

        Specification<CustomerOrder> spec = Specification.allOf(
                FilterSpecs.in("status", filter.status()),
                FilterSpecs.equal("channel", filter.channel()),
                FilterSpecs.dateRange("createdAt", filter.from(), filter.to()),
                withItemType(filter.itemType()),
                matching(filter.q()));

        return orders.findAll(spec, pageable).map(mapper::toResponse);
    }

    private static Specification<CustomerOrder> withItemType(OrderItemType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> lines = query.subquery(Long.class);
            Root<OrderItem> item = lines.from(OrderItem.class);
            lines.select(item.get("id")).where(
                    cb.equal(item.get("order"), root),
                    cb.equal(item.get("itemType"), type));

            return cb.exists(lines);
        };
    }

    private static Specification<CustomerOrder> matching(String search) {
        if (!FilterSpecs.hasText(search)) {
            return null;
        }
        return (root, query, cb) -> {
            Predicate text = FilterSpecs.anyContains(cb, search, root.<String>get("orderNumber"),
                    root.<String>get("customerName"), root.<String>get("customerEmail"));

            String trimmed = search.trim();
            return trimmed.matches("\\d{1,18}") ? cb.or(text, cb.equal(root.get("id"),
                    Long.parseLong(trimmed))) : text;
        };
    }

    /** Staff status change. Cancelling returns any stock the order still holds. */
    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {

        CustomerOrder order = orders.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (!TRANSITIONS.get(order.getStatus()).contains(newStatus)) {
            throw new InvalidOrderException(
                    "Cannot change order " + id + " from " + order.getStatus() + " to " + newStatus);
        }
        OrderStatus oldStatus = order.getStatus();
        if (newStatus == OrderStatus.CANCELLED) {

            closeAndReleaseStock(order, OrderStatus.CANCELLED);

        } else {
            order.setStatus(newStatus);
            orders.saveAndFlush(order);
        }
        audit.record(AuditAction.ORDER_STATUS, "ORDER", id, Map.of("status", oldStatus),
                Map.of("status", newStatus));

        return mapper.toResponse(orders.findWithItemsById(id).orElseThrow());
    }

    // payment-driven transitions, called by PaymentService inside its own
    // transaction

    /**
     * A payment covering the whole order arrived: the order is now paid and being
     * worked.
     */
    public void confirmAfterPayment(CustomerOrder order) {

        if (AWAITING_PAYMENT.contains(order.getStatus())) {

            order.setStatus(OrderStatus.CONFIRMED);
            order.setReservationExpiresAt(null);
        }
    }

    /**
     * Every payment attempt failed; the customer may retry until the reservation
     * runs out.
     */
    public void markPaymentFailed(CustomerOrder order) {

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {

            order.setStatus(OrderStatus.PAYMENT_FAILED);
        }
    }

    /**
     * A new payment attempt on a failed order puts it back to waiting, if its stock
     * hold is still valid.
     */
    public void reopenForPayment(CustomerOrder order) {

        if (!AWAITING_PAYMENT.contains(order.getStatus())) {

            return;
        }
        if (order.getReservationExpiresAt() != null && !order.getReservationExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessRuleException("The reservation for order " + order.getOrderNumber()
                    + " has expired; place the order again");
        }
        order.setStatus(OrderStatus.PENDING_PAYMENT);
    }

    // expiry of unpaid orders

    /**
     * Every minute: unpaid orders whose stock hold ran out become EXPIRED and give
     * their stock
     * back. Each order is handled in its own transaction so one failure cannot
     * block the rest.
     * An order with a payment in flight (PROCESSING) is left alone until that
     * settles.
     */
    @Scheduled(fixedDelayString = "${app.checkout.expiry-scan-delay:PT1M}", initialDelayString = "PT30S")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void expireStaleReservations() {

        List<Long> due = orders.findIdsByStatusAndReservationExpiredBefore(
                OrderStatus.PENDING_PAYMENT,
                OffsetDateTime.now(),
                PageRequest.of(0, 100));

        for (Long id : due) {
            try {
                transactions.executeWithoutResult(status -> expireIfStillDue(id));

            } catch (RuntimeException e) {
                log.error("Could not expire order {}", id, e);
            }
        }
    }

    private void expireIfStillDue(Long id) {

        CustomerOrder order = orders.findLockedById(id).orElse(null);

        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getReservationExpiresAt() == null
                || order.getReservationExpiresAt().isAfter(OffsetDateTime.now())) {
            return;
        }
        List<Payment> attempts = payments.findByOrderIdOrderByCreatedAtDesc(id);
        if (attempts.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PROCESSING)) {
            return;
        }
        attempts.stream().filter(p -> p.getStatus() == PaymentStatus.PENDING).forEach(Payment::markExpired);
        closeAndReleaseStock(order, OrderStatus.EXPIRED);
        log.info("Order {} expired unpaid; stock released", order.getOrderNumber());
    }

    /**
     * Moves the order to a closed status and returns the stock it still holds. The
     * product
     * ids are read first because incrementStock clears the persistence context.
     */
    private void closeAndReleaseStock(CustomerOrder order, OrderStatus closedStatus) {

        List<long[]> toRestock = new ArrayList<>();

        if (order.isStockReserved()) {

            for (OrderItem item : order.getItems()) {

                if (item.getItemType() == OrderItemType.PRODUCT) {

                    toRestock.add(new long[] { item.getProduct().getId(), item.getQuantity() });
                }
            }
        }
        order.setStatus(closedStatus);
        order.setStockReserved(false);
        orders.saveAndFlush(order);

        for (long[] line : toRestock) {

            products.incrementStock(line[0], (int) line[1]);
        }
    }

    /**
     * True when the email is the one the order was placed under (order header
     * first, then the linked customer).
     */
    public static boolean ownedBy(CustomerOrder order, String email) {

        String normalized = CustomerService.normalizeEmail(email);

        return normalized != null
                && (normalized.equals(order.getCustomerEmail())
                        || CustomerService.hasEmail(order.getCustomer(), email));
    }

    private static boolean isBlank(String value) {

        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {

        return value == null || value.isBlank() ? null : value.trim();
    }

    private OrderItem buildItem(CreateOrderRequest.Item line) {

        return switch (line.itemType()) {

            case PRODUCT -> {
                Product product = products.findById(line.referenceId())
                        .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                        .orElseThrow(() -> new ResourceNotFoundException("Product", line.referenceId()));
                yield OrderItem.ofProduct(product, line.quantity());
            }
            case MACHINE -> {
                MillingMachine machine = machines.findById(line.referenceId())
                        .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                        .orElseThrow(() -> new ResourceNotFoundException("Milling machine", line.referenceId()));
                yield OrderItem.ofMachine(machine, line.quantity());
            }
            case SERVICE -> {
                ServiceOffering service = serviceOfferings.findById(line.referenceId())
                        .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                        .orElseThrow(() -> new ResourceNotFoundException("Service", line.referenceId()));
                yield buildServiceItem(service, line);
            }
        };
    }

    private OrderItem buildServiceItem(ServiceOffering service,
            CreateOrderRequest.Item line) {

        return switch (service.getPricingType()) {

            case FIXED -> OrderItem.ofService(service,
                    line.quantity(), null,
                    service.getFixedPrice());

            case HOURLY -> {

                BigDecimal hours = line.estimatedHours();
                if (hours == null || hours.compareTo(service.getEstimatedMinHours()) < 0
                        || hours.compareTo(service.getEstimatedMaxHours()) > 0) {

                    throw new InvalidOrderException(service.getName() + " needs estimatedHours between "
                            + service.getEstimatedMinHours() + " and " + service.getEstimatedMaxHours());
                }
                BigDecimal unitPrice = service.getHourlyRate().multiply(hours).setScale(2, RoundingMode.HALF_UP);
                yield OrderItem.ofService(service, line.quantity(), hours, unitPrice);
            }
            case QUOTE ->
                throw new InvalidOrderException(service.getName()
                        + " is quote-only: request a price with POST /api/service-quotes");
        };
    }

    private Customer findOrCreateCustomer(CreateOrderRequest request) {

        if (request.customerName() == null || request.customerName().isBlank()) {

            return null;
        }
        return customers.findOrCreate(request.customerName(),
                request.customerEmail(),
                request.customerPhone());
    }
}
