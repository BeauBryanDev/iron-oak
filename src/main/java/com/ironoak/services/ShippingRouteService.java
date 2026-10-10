package com.ironoak.services;

import com.ironoak.domain.ShippingRoute;
import com.ironoak.domain.enums.ShippingSource;
import com.ironoak.exceptions.InvalidOrderException;
import com.ironoak.repository.ShippingRouteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Road distance from the warehouse to a destination city: the stored route if there is one,
 * otherwise (only when the caller allows it, i.e. a real order) one Google lookup that is then
 * stored, otherwise the straight-line estimate. Quotes for browsing never call Google. Inside
 * Colombia only the cities in ShippingCalculator.DOMESTIC_CITIES and staff-added routes are
 * served; any other Colombian town is refused.
 */
@Service
public class ShippingRouteService {

    private static final Logger log = LoggerFactory.getLogger(ShippingRouteService.class);

    /** Full country names for Google: a bare code is ambiguous ("Cali, CO" can resolve to Colorado). */
    private static final Map<String, String> COUNTRY_NAMES = Map.of(
            "CO", "Colombia", "EC", "Ecuador", "PE", "Peru", "BR", "Brazil", "AR", "Argentina",
            "BO", "Bolivia", "US", "United States", "MX", "Mexico", "CR", "Costa Rica", "PA", "Panama");

    /**
     * A road route is accepted only between these multiples of the straight-line estimate; outside
     * them Google most likely matched the wrong place, and the route is not stored.
     */
    private static final double MIN_ROAD_FACTOR = 0.5;
    private static final double MAX_ROAD_FACTOR = 3.0;

    public record Route(BigDecimal distanceKm, ShippingSource source) {
    }

    public record RefreshResult(int known, int alreadyStored, int fetched, int notFound, int googleCallsLeft) {
    }

    private final ShippingRouteRepository routes;
    private final ShippingRouteStore store;
    private final GoogleRoutesClient google;
    private final ShippingCalculator calculator;

    public ShippingRouteService(ShippingRouteRepository routes, ShippingRouteStore store,
                                GoogleRoutesClient google, ShippingCalculator calculator) {
        this.routes = routes;
        this.store = store;
        this.google = google;
        this.calculator = calculator;
    }

    public Route resolve(String country, String city, String province, boolean mayCallGoogle) {
        String key = ShippingCalculator.normalize(city);
        if (!key.isEmpty()) {
            var stored = routes.findByCountryAndCityKey(country, key);
            if (stored.isPresent()) {
                return new Route(stored.get().getDistanceKm(), stored.get().getSource());
            }
            // Inside Colombia we deliver only to the listed cities (and routes staff add by hand).
            if (ShippingCalculator.HOME_COUNTRY.equals(country)
                    && !ShippingCalculator.DOMESTIC_CITIES.containsKey(key)) {
                throw new InvalidOrderException("We do not deliver to '" + city.trim() + "' yet");
            }
            if (mayCallGoogle) {
                OptionalDouble km = google.roadDistanceKm(destination(city, province, country));
                if (km.isPresent() && plausible(country, city, province, km.getAsDouble())) {
                    return new Route(remember(country, key, city.trim(), km.getAsDouble()).getDistanceKm(),
                            ShippingSource.GOOGLE);
                }
            }
        }
        return new Route(calculator.distanceKm(country, city, province), ShippingSource.ESTIMATE);
    }

    public List<ShippingRoute> list() {
        return routes.findAllByOrderByCountryAscCityNameAsc();
    }

    /** Staff override: this distance is used from now on, whatever Google says. */
    public ShippingRoute setManual(String country, String city, BigDecimal distanceKm) {
        String code = ShippingCalculator.requireSupported(country);
        if (ShippingCalculator.isAir(code)) {
            throw new com.ironoak.exceptions.BusinessRuleException(code + " ships by air at a fixed price; it has no road route");
        }
        String key = ShippingCalculator.normalize(city);
        if (key.isEmpty()) {
            throw new com.ironoak.exceptions.BusinessRuleException("city is required");
        }
        return store.save(code, key, city.trim(), distanceKm.setScale(1, RoundingMode.HALF_UP), ShippingSource.MANUAL);
    }

    /**
     * Fills the routes we know we ship to (the Colombian cities and the 10 major cities of each
     * road country in ShippingCalculator) that are not stored yet. Uses the daily Google cap.
     */
    public RefreshResult refreshKnownCities() {
        Map<String, List<String>> known = new LinkedHashMap<>();
        known.put("CO", new ArrayList<>(ShippingCalculator.DOMESTIC_CITIES.keySet()));
        known.putAll(new java.util.TreeMap<>(ShippingCalculator.ROAD_COUNTRY_CITIES));
        int total = 0;
        int stored = 0;
        int fetched = 0;
        int notFound = 0;
        for (var entry : known.entrySet()) {
            for (String city : entry.getValue()) {
                total++;
                String key = ShippingCalculator.normalize(city);
                if (routes.findByCountryAndCityKey(entry.getKey(), key).isPresent()) {
                    stored++;
                    continue;
                }
                OptionalDouble km = google.roadDistanceKm(destination(city, null, entry.getKey()));
                if (km.isPresent() && plausible(entry.getKey(), city, null, km.getAsDouble())) {
                    remember(entry.getKey(), key, city, km.getAsDouble());
                    fetched++;
                } else {
                    notFound++;
                }
            }
        }
        return new RefreshResult(total, stored, fetched, notFound, google.remainingToday());
    }

    private ShippingRoute remember(String country, String key, String cityName, double km) {
        BigDecimal distance = BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP);
        try {
            return store.save(country, key, cityName, distance, ShippingSource.GOOGLE);
        } catch (DataIntegrityViolationException raced) {
            // another order stored the same city a moment ago
            log.debug("Route {}/{} was stored concurrently", country, key);
            return routes.findByCountryAndCityKey(country, key).orElseThrow();
        }
    }

    private boolean plausible(String country, String city, String province, double roadKm) {
        double estimate = calculator.distanceKm(country, city, province).doubleValue();
        if (estimate < 50) {
            return roadKm < 150; // inside or next to Bogota
        }
        double factor = roadKm / estimate;
        if (factor < MIN_ROAD_FACTOR || factor > MAX_ROAD_FACTOR) {
            log.warn("Ignoring Google route to {}, {}: {} km by road vs {} km straight; likely the wrong place",
                    city, country, Math.round(roadKm), Math.round(estimate));
            return false;
        }
        return true;
    }

    private static String destination(String city, String province, String country) {
        StringBuilder text = new StringBuilder(city.trim());
        if (province != null && !province.isBlank()) {
            text.append(", ").append(province.trim());
        }
        return text.append(", ").append(COUNTRY_NAMES.getOrDefault(country, country)).toString();
    }
}
