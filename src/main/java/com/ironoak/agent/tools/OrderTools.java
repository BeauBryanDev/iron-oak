package com.ironoak.agent.tools;

import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.dto.request.CreateOrderRequest;
import com.ironoak.dto.response.OrderResponse;
import com.ironoak.dto.response.PublicOrderResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.ironoak.services.CountryCodes;
import com.ironoak.services.OrderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Piper's order tools. Piper creates orders but never handles money. */
@Component
public class OrderTools {

        private final OrderService orders;

        public OrderTools(OrderService orders) {
                this.orders = orders;
        }

        @Tool(name = "create_order", description = "Create a customer order from catalog reference_ids; the backend prices items, shipping and taxes and holds stock for 24 hours. The order stays PENDING_PAYMENT: give the customer the returned orderNumber to pay online. Name and email are always required; products, end mills and machines also need country, city and shippingAddress (take them from what the customer wrote; ask only for what is missing). Reuse the idempotency key when retrying the same confirmed request.")
        public OrderResponse createOrder(
                        @ToolParam(description = "Customer contact and, when physical goods are ordered, delivery details.") Customer customer,
                        @ToolParam(description = "Catalog lines to order. itemType is PRODUCT, END_MILL, MACHINE or SERVICE; reference_id is the reference_id returned for that item by search_products, get_product_details, get_end_mills, list_machines or list_services (never the SKU, never a guess); quantity must be positive.") List<Item> items,
                        @ToolParam(description = "Stable retry key for this confirmed order request.") String idempotency_key) {

                CreateOrderRequest request = new CreateOrderRequest(
                                customer.name(),
                                customer.email(),
                                customer.phone(),
                                CountryCodes.toIsoCode(customer.country()),
                                customer.province(),
                                customer.city(),
                                customer.shippingAddress(),
                                items.stream().map(item -> new CreateOrderRequest.Item(
                                                item.itemType(),
                                                item.reference_id(),
                                                item.quantity(),
                                                item.estimatedHours())).toList());

                return orders.create(request,
                                OrderChannel.PIPER,
                                idempotency_key);
        }

        @Tool(name = "get_order", description = "Look up an order by its customer-facing order number "
                        + "(IO-YYYYMMDD-XXXXXXXX; spaces, lower case or missing dashes are fine). The number "
                        + "alone returns the order's status, items, totals, whether it can be paid now and "
                        + "its shipment (carrier, tracking, expected delivery), without personal details. "
                        + "Add the email used for the order to also get the name, email, phone and delivery "
                        + "address on file (contact_verified true). Never ask for the email just to give "
                        + "the status; never use the database id.")
        public OrderLookup getOrder(
                        @ToolParam(description = "Customer-facing order number, for example "
                                        + "IO-20261012-ABCD2345.") String order_number,
                        @ToolParam(description = "Email used for the order; optional, only needed "
                                        + "to show the contact and delivery details.", required = false) String email) {

                if (email != null && !email.isBlank()) {
                        try {
                                return new OrderLookup(true,
                                                null,
                                                orders.getForCustomer(order_number, email));
                        } catch (ResourceNotFoundException e) {
                                // wrong email: fall back to the public view below
                        }
                }
                return new OrderLookup(false,
                                orders.getPublic(order_number),
                                null);
        }

        /**
         * Exactly one of order / full_order is set: full_order (with contact and
         * address) only when the email matched the order.
         */
        public record OrderLookup(
                        @JsonProperty("contact_verified") boolean contactVerified,
                        PublicOrderResponse order,
                        @JsonProperty("full_order") OrderResponse fullOrder) {
        }

        public record Customer(
                        @JsonPropertyDescription("Customer full name.") String name,
                        @JsonPropertyDescription("Customer email; the order and its payment link are tied to it.") String email,
                        @JsonPropertyDescription("Phone number as given, optional.") String phone,
                        @JsonPropertyDescription("Delivery country. Either its name as the customer wrote it "
                                        + "(Colombia, Mexico, Estados Unidos...) or the ISO code (CO, MX, US); the tool "
                                        + "converts names. Iron & Oak delivers to CO, EC, PE, BR, AR, BO, PA, CR, MX, US.") String country,
                        @JsonPropertyDescription("State, department or province, optional.") String province,
                        @JsonPropertyDescription("Delivery city on its own, for example Medellin or Bogota; "
                                        + "shipping is priced by city.") String city,
                        @JsonPropertyDescription("Street address: street, number and any apartment or reference "
                                        + "the customer gave. Street and number are enough; do not ask for a neighbourhood.") String shippingAddress) {
        }

        public record Item(OrderItemType itemType,
                        long reference_id,
                        int quantity,
                        BigDecimal estimatedHours) {
        }
}
