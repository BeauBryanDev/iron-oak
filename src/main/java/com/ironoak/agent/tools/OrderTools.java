package com.ironoak.agent.tools;

import com.ironoak.domain.enums.OrderChannel;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.dto.request.CreateOrderRequest;
import com.ironoak.dto.response.OrderResponse;
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

        @Tool(name = "create_order", description = "Create a Piper customer order from catalog item references. The backend calculates prices, shipping, taxes, stock reservations, and the final total. The order remains PENDING_PAYMENT; Piper never takes payment: give the customer the returned orderNumber, they pay it online with that number (the items are held for 24 hours). Name and email are always required; for products, end mills and machines also country, city and shippingAddress. Take these from what the customer already wrote (split city and country out of a full address yourself, fix obvious typos in well-known city names) and only ask for what is truly missing. Use the same idempotency key when retrying the same confirmed request.")
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

        @Tool(name = "get_order", description = "Retrieve a customer's order using the customer-facing order number and the email used for the order. Both values are required; the database primary-key id is not a customer lookup identifier.")
        public OrderResponse getOrder(
                        @ToolParam(description = "Customer-facing order number, for example IO-20261012-ABCD2345.") String order_number,
                        @ToolParam(description = "Email address used when the order was created.") String email) {

                return orders.getForCustomer(order_number, email);
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
