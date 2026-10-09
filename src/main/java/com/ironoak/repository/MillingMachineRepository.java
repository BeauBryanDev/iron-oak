package com.ironoak.repository;

import com.ironoak.domain.MillingMachine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface MillingMachineRepository extends JpaRepository<MillingMachine, Long>, JpaSpecificationExecutor<MillingMachine> {

    Optional<MillingMachine> findByModelCode(String modelCode);

    List<MillingMachine> findByIsActiveTrueOrderByPriceAsc();
}
