package com.ironoak.services;

import com.ironoak.exceptions.BusinessRuleException;

import java.util.Locale;
import java.util.Map;

/**
 * Where Iron & Oak has technicians. Every human service (bookings, service lines in web and
 * Piper orders, quote requests) must take place in one of these cities; anywhere else is refused.
 */
public final class ServiceArea {

    public static final String SERVICE_COUNTRY = "CO";

    /** Normalized city key (see ShippingCalculator.normalize) -> display name. */
    public static final Map<String, String> SERVICE_CITIES = Map.of(
            "bogota", "Bogotá",
            "medellin", "Medellín");

    private ServiceArea() {
    }

    /** True for Bogotá or Medellín, Colombia, in any case or accents ("Bogotá D.C." included). */
    public static boolean covers(String country, String city) {
        if (country == null || !SERVICE_COUNTRY.equals(country.trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        String key = ShippingCalculator.normalize(city);
        return SERVICE_CITIES.keySet().stream().anyMatch(c -> key.equals(c) || key.startsWith(c + " "));
    }

    /** 422 unless the place is inside the service area. */
    public static void require(String country, String city) {
        if (!covers(country, city)) {
            throw new BusinessRuleException("Technician services are only available in "
                    + String.join(" and ", SERVICE_CITIES.values().stream().sorted().toList())
                    + ", Colombia");
        }
    }
}
