package com.ironoak.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.config.ShippingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Road distance from the warehouse via the Google Routes API (computeRoutes,
 * DRIVE). Every call
 * is billed, so this is used only to fill missing shipping_route rows, never
 * for browsing
 * quotes, and at most app.shipping.google-daily-cap times per UTC day
 * (in-memory counter, per
 * instance). Any failure, an exhausted cap or "no road route" returns empty and
 * the caller falls
 * back to the estimate; checkout never fails because of Maps. The key is never
 * logged.
 */
@Component
public class GoogleRoutesClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleRoutesClient.class);
    private static final URI ENDPOINT = URI.create("https://routes.googleapis.com/directions/v2:computeRoutes");

    private final ShippingProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private LocalDate quotaDay = LocalDate.now(ZoneOffset.UTC);
    private int usedToday;

    public GoogleRoutesClient(ShippingProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public boolean isConfigured() {
        return properties.getGoogleApiKey() != null && !properties.getGoogleApiKey().isBlank();
    }

    public synchronized int remainingToday() {
        rollDay();
        return Math.max(0, properties.getGoogleDailyCap() - usedToday);
    }

    /**
     * Road km from the origin to a free-text destination ("Medellin, Antioquia,
     * Colombia"), or empty.
     */
    public OptionalDouble roadDistanceKm(String destination) {
        if (!isConfigured() || !takeQuota()) {

            return OptionalDouble.empty();
        }
        try {
            String body = json.writeValueAsString(Map.of(
                    "origin", origin(),
                    "destination", Map.of("address", destination),
                    "travelMode", "DRIVE"));
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("X-Goog-Api-Key", properties.getGoogleApiKey())
                    .header("X-Goog-FieldMask", "routes.distanceMeters")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {

                log.warn("Google Routes answered {} for '{}'",
                        response.statusCode(), destination);
                return OptionalDouble.empty();
            }
            JsonNode distance = json.readTree(response.body()).path("routes").path(0).path("distanceMeters");
            if (!distance.isNumber()) {

                log.info("Google Routes found no road route to '{}'", destination);
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(distance.asDouble() / 1000.0);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            return OptionalDouble.empty();

        } catch (Exception e) {
            log.warn("Google Routes call for '{}' failed: {}",
                    destination, e.toString());
            return OptionalDouble.empty();
        }
    }

    /**
     * The warehouse: its coordinates when configured (exact), otherwise its
     * address.
     */
    private Map<String, Object> origin() {

        String location = properties.getOriginLocation();

        if (location != null && !location.isBlank()) {

            String[] parts = location.replaceAll("[\\[\\]\\s]", "").split(",");

            if (parts.length == 2) {
                try {
                    return Map.of("location",
                            Map.of("latLng", Map.of(
                                    "latitude",
                                    Double.parseDouble(parts[0]),
                                    "longitude",
                                    Double.parseDouble(parts[1]))));
                } catch (NumberFormatException e) {

                    log.warn("app.shipping.origin-location is not \"lat, lon\"; using the address");
                }
            }
        }
        return Map.of("address", properties.getOriginAddress());
    }

    private synchronized boolean takeQuota() {
        rollDay();

        if (usedToday >= properties.getGoogleDailyCap()) {

            log.warn("Google Routes daily cap of {} reached; using estimates until tomorrow (UTC)",
                    properties.getGoogleDailyCap());

            return false;
        }
        usedToday++;
        return true;
    }

    private void rollDay() {

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        if (!today.equals(quotaDay)) {

            quotaDay = today;
            usedToday = 0;
        }
    }
}
