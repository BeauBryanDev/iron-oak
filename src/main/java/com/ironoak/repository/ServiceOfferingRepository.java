package com.ironoak.repository;

import com.ironoak.domain.ServiceOffering;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long>, JpaSpecificationExecutor<ServiceOffering> {

    Optional<ServiceOffering> findByCode(String code);

    @EntityGraph(attributePaths = "category")
    List<ServiceOffering> findByIsActiveTrueOrderByCategoryNameAscNameAsc();
}
