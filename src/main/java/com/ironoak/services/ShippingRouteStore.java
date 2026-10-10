package com.ironoak.services;

import com.ironoak.domain.ShippingRoute;
import com.ironoak.domain.enums.ShippingSource;
import com.ironoak.repository.ShippingRouteRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Writes routes in their own transaction, so a route learned while placing an order is kept even
 * if the order fails, and two orders learning the same city at once cannot break each other.
 */
@Component
public class ShippingRouteStore {

    private final ShippingRouteRepository routes;

    public ShippingRouteStore(ShippingRouteRepository routes) {
        this.routes = routes;
    }

    /** Inserts or overwrites the route for (country, cityKey). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShippingRoute save(String country, String cityKey, String cityName, BigDecimal km, ShippingSource source) {
        ShippingRoute route = routes.findByCountryAndCityKey(country, cityKey)
                .orElseGet(() -> new ShippingRoute(country, cityKey, cityName, km, source));
        route.setCityName(cityName);
        route.setDistanceKm(km);
        route.setSource(source);
        return routes.saveAndFlush(route);
    }
}
