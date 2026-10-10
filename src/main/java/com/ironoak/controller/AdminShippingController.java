package com.ironoak.controller;

import com.ironoak.domain.ShippingRoute;
import com.ironoak.services.ShippingRouteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Staff view and control of the stored road distances (ROLE_ADMIN via
 * /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/shipping/routes")
public class AdminShippingController {

    public record RouteResponse(Long id,
            String country,
            String city,
            BigDecimal distanceKm,
            String source,
            OffsetDateTime updatedAt) {

        static RouteResponse of(ShippingRoute route) {

            return new RouteResponse(route.getId(),
                    route.getCountry(),
                    route.getCityName(),
                    route.getDistanceKm(),
                    route.getSource().name(),
                    route.getUpdatedAt());
        }
    }

    public record ManualRouteRequest(@NotBlank @Size(min = 2, max = 2) String country,
            @NotBlank @Size(max = 100) String city,
            @NotNull @DecimalMin("0.0") BigDecimal distanceKm) {
    }

    private final ShippingRouteService routes;

    public AdminShippingController(ShippingRouteService routes) {
        this.routes = routes;
    }

    @GetMapping
    public List<RouteResponse> list() {
        return routes.list().stream().map(RouteResponse::of).toList();
    }

    /**
     * Sets a distance by hand (also how a new Colombian town is opened for
     * delivery).
     */
    @PutMapping
    public RouteResponse setManual(@Valid @RequestBody ManualRouteRequest request) {

        return RouteResponse.of(routes.setManual(request.country(),
                request.city(),
                request.distanceKm()));
    }

    /**
     * Fetches the road distance of every known city not stored yet, within the
     * daily Google cap.
     */
    @PostMapping("/refresh")
    public ShippingRouteService.RefreshResult refresh() {

        return routes.refreshKnownCities();
    }
}
