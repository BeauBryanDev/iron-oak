package com.ironoak.services;

import com.ironoak.domain.OrderItem;
import com.ironoak.domain.enums.OrderItemType;
import com.ironoak.exceptions.InvalidOrderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Iron & Oak shipping cost, in USD, from Bogota.
 *
 * Each shipment group is priced with its own formula:
 * cost = PER_100KM * distance + PER_KG * weight + PER_M3 * volume + BASE
 * where distance is the straight-line distance from Bogota in units of 100 km,
 * weight is in kg
 * and volume in cubic metres. All products in an order ship together as one
 * "tools" shipment
 * (total weight and volume, one base fee); all milling machines ship as one
 * "CNC" shipment.
 * Services are not shipped. Colombia uses the domestic formulas, every other
 * supported country
 * the international ones.
 */
@Component
public class ShippingCalculator {

    private static final Logger log = LoggerFactory.getLogger(ShippingCalculator.class);

    // Power tools (products), outside Colombia
    static final BigDecimal TOOLS_INTL_PER_100KM = new BigDecimal("0.27");
    static final BigDecimal TOOLS_INTL_PER_KG = new BigDecimal("0.25");
    static final BigDecimal TOOLS_INTL_PER_M3 = new BigDecimal("15");
    static final BigDecimal TOOLS_INTL_BASE = new BigDecimal("7.5");

    // Power tools (products), inside Colombia-
    static final BigDecimal TOOLS_DOMESTIC_PER_100KM = new BigDecimal("0.215");
    static final BigDecimal TOOLS_DOMESTIC_PER_KG = new BigDecimal("0.275");
    static final BigDecimal TOOLS_DOMESTIC_PER_M3 = new BigDecimal("17.5");
    static final BigDecimal TOOLS_DOMESTIC_BASE = new BigDecimal("2.5");

    // Milling machines, outside Colombia
    static final BigDecimal CNC_INTL_PER_100KM = new BigDecimal("0.32");
    static final BigDecimal CNC_INTL_PER_KG = new BigDecimal("0.46");
    static final BigDecimal CNC_INTL_PER_M3 = new BigDecimal("2.5");
    static final BigDecimal CNC_INTL_BASE = new BigDecimal("125");

    // Milling machines, inside Colombia
    static final BigDecimal CNC_DOMESTIC_PER_100KM = new BigDecimal("0.45");
    static final BigDecimal CNC_DOMESTIC_PER_KG = new BigDecimal("0.56");
    static final BigDecimal CNC_DOMESTIC_PER_M3 = new BigDecimal("2.1");
    static final BigDecimal CNC_DOMESTIC_BASE = new BigDecimal("48");

    // Missing catalog data
    /**
     * A product or machine without weight/volume cannot be priced by the formulas.
     * false: the
     * missing value counts as zero (only distance and the base fee are charged for
     * it) and a
     * warning is logged. true: the order is refused. Switch to true before going
     * live.
     */
    static final boolean FAIL_ON_MISSING_DIMENSIONS = false;

    // Origin and distance model
    static final String HOME_COUNTRY = "CO";
    static final double BOGOTA_LAT = 4.7110;
    static final double BOGOTA_LON = -74.0721;
    static final double EARTH_RADIUS_KM = 6371.0;
    /** Distance unit used by the formulas: 100 km. */
    static final BigDecimal KM_PER_DISTANCE_UNIT = new BigDecimal("100");
    /**
     * Used for a Colombian destination that is not in DOMESTIC_CITIES (assumption:
     * 800 km).
     */
    static final BigDecimal DOMESTIC_FALLBACK_DISTANCE_KM = new BigDecimal("800");

    /**
     * Countries we deliver to (Colombia plus the nine listed international
     * destinations).
     */
    static final Set<String> SUPPORTED_COUNTRIES = Set.of(
            "CO", "US", "MX", "CR", "EC", "PA", "PE", "BR", "AR", "BO");

    /**
     * International distance is measured to each country's capital: {latitude,
     * longitude}.
     */
    static final Map<String, double[]> COUNTRY_CAPITALS = Map.of(
            "US", new double[] { 38.9072, -77.0369 }, // Washington, D.C.
            "MX", new double[] { 19.4326, -99.1332 }, // Mexico City
            "CR", new double[] { 9.9281, -84.0907 }, // San Jose
            "EC", new double[] { -0.1807, -78.4678 }, // Quito
            "PA", new double[] { 8.9824, -79.5199 }, // Panama City
            "PE", new double[] { -12.0464, -77.0428 }, // Lima
            "BR", new double[] { -15.7939, -47.8828 }, // Brasilia
            "AR", new double[] { -34.6037, -58.3816 }, // Buenos Aires
            "BO", new double[] { -16.4897, -68.1193 }); // La Paz

    /**
     * Colombian destinations, by lower-case name without accents: {latitude,
     * longitude}.
     */
    static final Map<String, double[]> DOMESTIC_CITIES = Map.ofEntries(
            Map.entry("bogota", new double[] { 4.7110, -74.0721 }),
            Map.entry("medellin", new double[] { 6.2442, -75.5812 }),
            Map.entry("cali", new double[] { 3.4516, -76.5320 }),
            Map.entry("barranquilla", new double[] { 10.9685, -74.7813 }),
            Map.entry("cartagena", new double[] { 10.3910, -75.4794 }),
            Map.entry("bucaramanga", new double[] { 7.1193, -73.1227 }),
            Map.entry("cucuta", new double[] { 7.8939, -72.5078 }),
            Map.entry("pereira", new double[] { 4.8133, -75.6961 }),
            Map.entry("manizales", new double[] { 5.0703, -75.5138 }),
            Map.entry("santa marta", new double[] { 11.2408, -74.1990 }),
            Map.entry("ibague", new double[] { 4.4389, -75.2322 }),
            Map.entry("pasto", new double[] { 1.2136, -77.2811 }),
            Map.entry("villavicencio", new double[] { 4.1420, -73.6266 }),
            Map.entry("neiva", new double[] { 2.9273, -75.2819 }),
            Map.entry("armenia", new double[] { 4.5339, -75.6811 }),
            Map.entry("monteria", new double[] { 8.7479, -75.8814 }),
            Map.entry("valledupar", new double[] { 10.4631, -73.2532 }),
            Map.entry("popayan", new double[] { 2.4448, -76.6147 }),
            Map.entry("tunja", new double[] { 5.5353, -73.3678 }),
            Map.entry("sincelejo", new double[] { 9.3047, -75.3978 }),
            Map.entry("riohacha", new double[] { 11.5444, -72.9072 }),
            Map.entry("florencia", new double[] { 1.6144, -75.6062 }),
            Map.entry("quibdo", new double[] { 5.6919, -76.6583 }),
            Map.entry("yopal", new double[] { 5.3378, -72.3959 }),
            Map.entry("arauca", new double[] { 7.0847, -70.7591 }),
            Map.entry("mocoa", new double[] { 1.1478, -76.6479 }),
            Map.entry("leticia", new double[] { -4.2153, -69.9406 }),
            Map.entry("san andres", new double[] { 12.5847, -81.7006 }),
            Map.entry("san jose del guaviare", new double[] { 2.5729, -72.6459 }),
            Map.entry("inirida", new double[] { 3.8653, -67.9239 }),
            Map.entry("mitu", new double[] { 1.2530, -70.2340 }),
            Map.entry("puerto carreno", new double[] { 6.1890, -67.4859 }));

    /** Price of one order's shipping. Groups with nothing to ship cost zero. */
    public record Quote(String country, boolean domestic, BigDecimal distanceKm,
            BigDecimal toolsCost, BigDecimal machinesCost, BigDecimal total) {
    }

    /**
     * The shipping cost of the physical lines of an order (products and machines).
     */
    public Quote quote(String country,
            String city,
            String province,
            List<OrderItem> items) {

        BigDecimal toolsKg = BigDecimal.ZERO;
        BigDecimal toolsM3 = BigDecimal.ZERO;
        BigDecimal machinesKg = BigDecimal.ZERO;
        BigDecimal machinesM3 = BigDecimal.ZERO;
        boolean tools = false;
        boolean machines = false;
        for (OrderItem item : items) {
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
            if (item.getItemType() == OrderItemType.PRODUCT) {
                tools = true;
                toolsKg = toolsKg.add(dimension(item.getProduct().getWeightKg(), item.getItemCode()).multiply(qty));
                toolsM3 = toolsM3.add(dimension(item.getProduct().getVolumeM3(), item.getItemCode()).multiply(qty));
            } else if (item.getItemType() == OrderItemType.MACHINE) {
                machines = true;
                machinesKg = machinesKg
                        .add(dimension(item.getMillingMachine().getWeightKg(), item.getItemCode()).multiply(qty));
                machinesM3 = machinesM3
                        .add(dimension(item.getMillingMachine().getVolumeM3(), item.getItemCode()).multiply(qty));
            }
        }
        return quote(country, city, province, tools,
                toolsKg, toolsM3, machines, machinesKg, machinesM3);
    }

    private static BigDecimal dimension(BigDecimal value, String itemCode) {
        if (value != null) {
            return value;
        }
        if (FAIL_ON_MISSING_DIMENSIONS) {
            throw new InvalidOrderException("Shipping weight or volume is not set for " + itemCode);
        }
        log.warn("Shipping weight/volume missing for {}; counted as zero", itemCode);
        return BigDecimal.ZERO;
    }

    /**
     * Same, from already-totalled weights and volumes (also what the unit tests
     * call).
     */
    Quote quote(String country,
            String city,
            String province,
            boolean hasTools,
            BigDecimal toolsKg,
            BigDecimal toolsM3,
            boolean hasMachines,
            BigDecimal machinesKg,
            BigDecimal machinesM3) {

        String code = country == null ? "" : country.trim().toUpperCase(Locale.ROOT);

        if (!hasTools && !hasMachines) {

            return new Quote(code, HOME_COUNTRY.equals(code),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }
        if (!SUPPORTED_COUNTRIES.contains(code)) {

            throw new InvalidOrderException("We do not deliver to '" + code
                    + "'. Supported countries: CO, US, MX, CR, EC, PA, PE, BR, AR, BO");
        }
        boolean domestic = HOME_COUNTRY.equals(code);
        BigDecimal distanceKm = distanceKm(code, city, province);
        BigDecimal distance = distanceKm.divide(KM_PER_DISTANCE_UNIT, 6, RoundingMode.HALF_UP);
        BigDecimal toolsCost = BigDecimal.ZERO;

        if (hasTools) {
            toolsCost = domestic
                    ? price(distance, toolsKg, toolsM3,
                            TOOLS_DOMESTIC_PER_100KM, TOOLS_DOMESTIC_PER_KG,
                            TOOLS_DOMESTIC_PER_M3, TOOLS_DOMESTIC_BASE)
                    : price(distance, toolsKg, toolsM3,
                            TOOLS_INTL_PER_100KM, TOOLS_INTL_PER_KG,
                            TOOLS_INTL_PER_M3, TOOLS_INTL_BASE);
        }
        BigDecimal machinesCost = BigDecimal.ZERO;

        if (hasMachines) {

            machinesCost = domestic
                    ? price(distance, machinesKg, machinesM3,
                            CNC_DOMESTIC_PER_100KM, CNC_DOMESTIC_PER_KG,
                            CNC_DOMESTIC_PER_M3, CNC_DOMESTIC_BASE)
                    : price(distance, machinesKg, machinesM3,
                            CNC_INTL_PER_100KM, CNC_INTL_PER_KG,
                            CNC_INTL_PER_M3, CNC_INTL_BASE);
        }
        return new Quote(code, domestic, distanceKm, toolsCost,
                machinesCost, toolsCost.add(machinesCost));
    }

    private static BigDecimal price(BigDecimal distance, BigDecimal kg, BigDecimal m3,
            BigDecimal perDistance, BigDecimal perKg, BigDecimal perM3, BigDecimal base) {

        return perDistance.multiply(distance)
                .add(perKg.multiply(kg))
                .add(perM3.multiply(m3))
                .add(base)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Straight-line distance from Bogota, in km. Colombia is matched by city, then
     * province.
     */
    BigDecimal distanceKm(String code, String city, String province) {

        double[] destination;
        if (HOME_COUNTRY.equals(code)) {

            destination = DOMESTIC_CITIES.get(normalize(city));

            if (destination == null) {
                destination = DOMESTIC_CITIES.get(normalize(province));
            }
            if (destination == null) {
                return DOMESTIC_FALLBACK_DISTANCE_KM;
            }
        } else {
            destination = COUNTRY_CAPITALS.get(code);
        }
        return BigDecimal.valueOf(haversineKm(BOGOTA_LAT, BOGOTA_LON,
                destination[0], destination[1]))
                .setScale(1, RoundingMode.HALF_UP);
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
