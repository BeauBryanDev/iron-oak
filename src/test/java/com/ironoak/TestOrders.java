package com.ironoak;

import com.ironoak.dto.request.CreateOrderRequest;
import com.ironoak.dto.request.CreateOrderRequest.Item;

import java.util.List;

/**
 * Order requests for tests. Web and Piper orders need a contact and, for goods, an address;
 * staff orders (ADMIN_MANUAL) need neither. The address is Bogota, so the distance part of
 * shipping is zero and only the base fees apply (the seed has no weights or volumes yet).
 */
public final class TestOrders {

    public static final String COUNTRY = "CO";
    public static final String CITY = "Bogota";
    public static final String ADDRESS = "Calle 1 # 2-3";

    private TestOrders() {
    }

    public static CreateOrderRequest web(String name, String email, List<Item> items) {
        return new CreateOrderRequest(name, email, null, COUNTRY, null, CITY, ADDRESS, items);
    }

    public static CreateOrderRequest staff(List<Item> items) {
        return new CreateOrderRequest(null, null, null, null, null, null, null, items);
    }

    /** JSON body for POST /api/orders with one line. */
    public static String webJson(String name, String email, String itemType, long referenceId, int quantity) {
        return "{\"customerName\":\"" + name + "\",\"customerEmail\":\"" + email + "\",\"country\":\"" + COUNTRY
                + "\",\"city\":\"" + CITY + "\",\"shippingAddress\":\"" + ADDRESS + "\",\"items\":[{\"itemType\":\""
                + itemType + "\",\"referenceId\":" + referenceId + ",\"quantity\":" + quantity + "}]}";
    }
}
