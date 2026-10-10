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
 * Iron & Oak shipping prices and constants, in USD, from Bogota. ShippingService decides which
 * of these applies; this class only does the arithmetic.
 *
 * Road shipments (Colombia and the South American countries) use:
 * cost = PER_100KM * distance + PER_KG * weight + PER_M3 * volume + BASE
 * where distance is the road distance from Bogota in units of 100 km (from the shipping_route
 * table, or the straight-line estimate below), weight is in kg and volume in cubic metres. All
 * products in an order ship together as one "tools" shipment (total weight and volume, one base
 * fee). Colombia uses the domestic formula, the other road countries the international one.
 *
 * AIR_COUNTRIES have no road from Colombia (Darien Gap): tools ship by air at a fixed price per
 * country. Milling machines are always quoted on request by staff; the CNC formulas are kept
 * only as an indicative figure for them. Services are not shipped.
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

    // Air freight (no road across the Darien Gap): fixed price per order's tools shipment.
    // A null price means "not set yet": such orders are quoted on request by staff.
    static final Set<String> AIR_COUNTRIES = Set.of("US", "MX", "CR", "PA");
    static final BigDecimal AIR_PRICE_US = new BigDecimal("86");   // USD, one tools shipment to the USA
    static final BigDecimal AIR_PRICE_MX = new BigDecimal("64");   // Mexico
    static final BigDecimal AIR_PRICE_CR = new BigDecimal("48");   // Costa Rica
    static final BigDecimal AIR_PRICE_PA = new BigDecimal("36");   // Panama

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
            Map.entry("florencia", new double[] { 1.6144, -75.6062 }),
            Map.entry("yopal", new double[] { 5.3378, -72.3959 }));

    /**
     * The 10 major cities of each road-reachable country, pre-filled into shipping_route. Other
     * cities there still ship: their route is fetched when an order is placed. Cities with no road
     * from Bogota (for example Iquitos) are left out.
     */
    static final Map<String, List<String>> ROAD_COUNTRY_CITIES = Map.of(
            "EC", List.of("Quito", "Guayaquil", "Cuenca", "Santo Domingo", "Machala",
                    "Manta", "Portoviejo", "Ambato", "Riobamba", "Loja"),
            "PE", List.of("Lima", "Arequipa", "Trujillo", "Chiclayo", "Piura",
                    "Cusco", "Huancayo", "Chimbote", "Tacna", "Ica"),
            "BR", List.of("Sao Paulo", "Rio de Janeiro", "Brasilia", "Salvador", "Fortaleza",
                    "Belo Horizonte", "Manaus", "Curitiba", "Recife", "Porto Alegre"),
            "AR", List.of("Buenos Aires", "Cordoba", "Rosario", "Mendoza", "San Miguel de Tucuman",
                    "La Plata", "Mar del Plata", "Salta", "Santa Fe", "San Juan"),
            "BO", List.of("Santa Cruz de la Sierra", "El Alto", "La Paz", "Cochabamba", "Oruro",
                    "Sucre", "Tarija", "Potosi", "Sacaba", "Montero"));

    /** What an order puts on the truck: totals per shipment group. */
    public record Load(boolean tools, BigDecimal toolsKg, BigDecimal toolsM3,
                       boolean machines, BigDecimal machinesKg, BigDecimal machinesM3) {

        public boolean shipsAnything() {
            return tools || machines;
        }
    }

    /** Totals the weight and volume of the physical lines of an order (products and machines). */
    public Load load(List<OrderItem> items) {

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
        return new Load(tools, toolsKg, toolsM3, machines, machinesKg, machinesM3);
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

    /** Upper-case ISO code; refuses countries we do not deliver to. */
    static String requireSupported(String country) {
        String code = country == null ? "" : country.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_COUNTRIES.contains(code)) {
            throw new InvalidOrderException("We do not deliver to '" + code
                    + "'. Supported countries: CO, US, MX, CR, EC, PA, PE, BR, AR, BO");
        }
        return code;
    }

    static boolean isAir(String code) {
        return AIR_COUNTRIES.contains(code);
    }

    /** The fixed air price for a country, or null when it has not been set. */
    static BigDecimal airPrice(String code) {
        return switch (code) {
            case "US" -> AIR_PRICE_US;
            case "MX" -> AIR_PRICE_MX;
            case "CR" -> AIR_PRICE_CR;
            case "PA" -> AIR_PRICE_PA;
            default -> null;
        };
    }

    /** Road price of the tools shipment for a road distance in km. */
    BigDecimal toolsCost(boolean domestic, BigDecimal distanceKm, BigDecimal kg, BigDecimal m3) {
        BigDecimal distance = distanceKm.divide(KM_PER_DISTANCE_UNIT, 6, RoundingMode.HALF_UP);
        return domestic
                ? price(distance, kg, m3, TOOLS_DOMESTIC_PER_100KM, TOOLS_DOMESTIC_PER_KG,
                        TOOLS_DOMESTIC_PER_M3, TOOLS_DOMESTIC_BASE)
                : price(distance, kg, m3, TOOLS_INTL_PER_100KM, TOOLS_INTL_PER_KG,
                        TOOLS_INTL_PER_M3, TOOLS_INTL_BASE);
    }

    /** Indicative road price of the machines shipment (machines are quoted on request). */
    BigDecimal machinesCost(boolean domestic, BigDecimal distanceKm, BigDecimal kg, BigDecimal m3) {
        BigDecimal distance = distanceKm.divide(KM_PER_DISTANCE_UNIT, 6, RoundingMode.HALF_UP);
        return domestic
                ? price(distance, kg, m3, CNC_DOMESTIC_PER_100KM, CNC_DOMESTIC_PER_KG,
                        CNC_DOMESTIC_PER_M3, CNC_DOMESTIC_BASE)
                : price(distance, kg, m3, CNC_INTL_PER_100KM, CNC_INTL_PER_KG,
                        CNC_INTL_PER_M3, CNC_INTL_BASE);
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
     * Straight-line estimate from Bogota, in km, used only when no road route is stored. Colombia
     * is matched by city, then province; other countries use their capital.
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

    /** Lower case, accents removed, single spaces: the key routes are stored under. */
    static String normalize(String name) {
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
