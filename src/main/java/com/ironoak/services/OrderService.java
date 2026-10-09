package com.ironoak.services;

import com.ironoak.domain.Customer;
import com.ironoak.domain.CustomerOrder;
import com.ironoak.domain.MillingMachine;
import com.ironoak.domain.OrderItem;
import com.ironoak.domain.Product;
import com.ironoak.domain.ServiceOffering;
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

@Service
@Transactional
public class OrderService {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.DRAFT, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED),
            OrderStatus.IN_PROGRESS, Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED),
            OrderStatus.COMPLETED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    private final CustomerOrderRepository orders;
    private final CustomerService customers;
    private final ProductRepository products;
    private final ServiceOfferingRepository serviceOfferings;
    private final MillingMachineRepository machines;
    private final OrderMapper mapper;

    public OrderService(CustomerOrderRepository orders,
            CustomerService customers,
            ProductRepository products,
            ServiceOfferingRepository serviceOfferings,
            MillingMachineRepository machines,
            OrderMapper mapper) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.serviceOfferings = serviceOfferings;
        this.machines = machines;
        this.mapper = mapper;
    }

    /**
     * Prices every line from the catalog (the client never sends prices), takes
     * product
     * stock atomically, and saves the order as CONFIRMED. Any failure rolls the
     * whole
     * order back, including stock already taken for earlier lines.
     */
    public OrderResponse create(CreateOrderRequest request, OrderChannel channel) {
        return create(request, channel, null);
    }

    /**
     * Same as above, but safe to retry: a repeated idempotencyKey returns the order
     * the
     * first call created instead of taking stock a second time.
     */
    public OrderResponse create(CreateOrderRequest request, OrderChannel channel, String idempotencyKey) {
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

        // decrementStock clears the persistence context, so it runs after all lookups.
        for (OrderItem item : items) {
            if (item.getItemType() == OrderItemType.PRODUCT
                    && products.decrementStock(item.getProduct().getId(), item.getQuantity()) == 0) {
                throw new OutOfStockException(item.getProduct().getSku(), item.getQuantity());
            }
        }

        CustomerOrder order = new CustomerOrder(findOrCreateCustomer(request), channel);
        order.setIdempotencyKey(key);
        items.forEach(order::addItem);
        return mapper.toResponse(orders.save(order));
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
                .filter(o -> CustomerService.hasEmail(o.getCustomer(), customerEmail))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(OrderStatus status, Pageable pageable) {
        Page<CustomerOrder> page = status == null
                ? orders.findAll(pageable)
                : orders.findByStatus(status, pageable);
        return page.map(mapper::toResponse);
    }

    /** Cancelling returns the stock taken for product lines. */
    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
        CustomerOrder order = orders.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (!TRANSITIONS.get(order.getStatus()).contains(newStatus)) {
            throw new InvalidOrderException(
                    "Cannot change order " + id + " from " + order.getStatus() + " to " + newStatus);
        }
        boolean cancelling = newStatus == OrderStatus.CANCELLED;
        // Capture the lines before incrementStock clears the persistence context.
        List<OrderItem> items = List.copyOf(order.getItems());
        order.setStatus(newStatus);
        orders.saveAndFlush(order);
        if (cancelling) {
            for (OrderItem item : items) {
                if (item.getItemType() == OrderItemType.PRODUCT) {
                    products.incrementStock(item.getProduct().getId(), item.getQuantity());
                }
            }
        }
        return mapper.toResponse(orders.findWithItemsById(id).orElseThrow());
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
            case FIXED -> OrderItem.ofService(service, line.quantity(), null, service.getFixedPrice());
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
                throw new InvalidOrderException(service.getName() + " is quote-only and cannot be ordered directly");
        };
    }

    private Customer findOrCreateCustomer(CreateOrderRequest request) {
        if (request.customerName() == null || request.customerName().isBlank()) {
            return null;
        }
        return customers.findOrCreate(request.customerName(), request.customerEmail(), request.customerPhone());
    }
}
