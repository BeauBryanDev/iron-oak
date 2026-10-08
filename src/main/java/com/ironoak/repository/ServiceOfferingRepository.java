package com.ironoak.repository;

import com.ironoak.domain.ServiceOffering;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    Optional<ServiceOffering> findByCode(String code);

    @EntityGraph(attributePaths = "category")
    List<ServiceOffering> findByIsActiveTrueOrderByCategoryNameAscNameAsc();
}
