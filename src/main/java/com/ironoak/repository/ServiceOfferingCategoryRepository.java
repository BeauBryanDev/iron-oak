package com.ironoak.repository;

import com.ironoak.domain.ServiceOfferingCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceOfferingCategoryRepository extends JpaRepository<ServiceOfferingCategory, Long> {

    List<ServiceOfferingCategory> findAllByOrderByNameAsc();
}
