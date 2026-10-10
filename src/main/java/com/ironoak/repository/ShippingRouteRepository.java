package com.ironoak.repository;

import com.ironoak.domain.ShippingRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShippingRouteRepository extends JpaRepository<ShippingRoute, Long> {

    Optional<ShippingRoute> findByCountryAndCityKey(String country, String cityKey);

    List<ShippingRoute> findAllByOrderByCountryAscCityNameAsc();
}
