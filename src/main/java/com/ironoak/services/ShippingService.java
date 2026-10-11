package com.ironoak.services;

import com.ironoak.domain.OrderItem;
import com.ironoak.domain.enums.ShippingMode;
import com.ironoak.domain.enums.ShippingSource;
import com.ironoak.domain.enums.ShippingStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * The one place that prices shipping, shared by the cart quote, the web
 * checkout and Piper, so
 * they can never disagree. Rules, in order:
 * nothing physical => free; a country we do not serve => refused;
 * any milling machine => quoted on request by staff;
 * an air country => its fixed air price, or on request while that
 * price is unset;
 * otherwise => the road formula with the city's road distance
 * mayCallGoogle is true only when a real order is being placed.
 */
@Service
public class ShippingService {

    /** The price and how it was reached. cost is 0 while status is ON_REQUEST. */
    public record Quote(ShippingStatus status,
            ShippingMode mode, String country,
            boolean domestic,
            BigDecimal distanceKm,
            ShippingSource source,
            BigDecimal cost,
            String note) {

        public static Quote nothingToShip() {
            return new Quote(ShippingStatus.QUOTED,
                    ShippingMode.NONE,
                    null, false,
                    null, null,
                    BigDecimal.ZERO, null);
        }
    }

    private final ShippingCalculator calculator;
    private final ShippingRouteService routes;

    public ShippingService(ShippingCalculator calculator,
            ShippingRouteService routes) {

        this.calculator = calculator;
        this.routes = routes;
    }

    public Quote quote(String country,
            String city,
            String province,
            List<OrderItem> items,
            boolean mayCallGoogle) {

        ShippingCalculator.Load load = calculator.load(items);

        if (!load.shipsAnything()) {

            return Quote.nothingToShip();
        }
        String code = ShippingCalculator.requireSupported(country);
        boolean domestic = ShippingCalculator.HOME_COUNTRY.equals(code);
        boolean air = ShippingCalculator.isAir(code);
        ShippingMode mode = air ? ShippingMode.AIR : ShippingMode.ROAD;

        if (load.machines()) {

            return onRequest(mode, code, domestic,
                    "Milling machines ship as freight; our staff will quote it");
        }
        if (air) {
            BigDecimal price = ShippingCalculator.airPrice(code);
            if (price == null) {
                return onRequest(mode, code, false,
                        "Air shipping to " + code + " is quoted by our staff");
            }
            return new Quote(ShippingStatus.QUOTED,
                    mode, code,
                    false, null,
                    ShippingSource.FIXED,
                    price, null);
        }

        ShippingRouteService.Route route = routes.resolve(code,
                city,
                province,
                mayCallGoogle);

        BigDecimal cost = calculator.toolsCost(domestic,
                route.distanceKm(),
                load.toolsKg(), load.toolsM3());

        String note = route.source() == ShippingSource.ESTIMATE
                ? "Estimated; confirmed when the order is placed"
                : null;

        return new Quote(ShippingStatus.QUOTED,
                mode, code,
                domestic, route.distanceKm(),
                route.source(), cost, note);
    }

    private static Quote onRequest(ShippingMode mode,
            String code,
            boolean domestic,
            String note) {

        return new Quote(ShippingStatus.ON_REQUEST,
                mode, code, domestic,
                null, null,
                BigDecimal.ZERO, note);
    }
}
