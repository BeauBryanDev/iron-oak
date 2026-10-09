package com.ironoak.repository;

import com.ironoak.domain.ToolCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ToolCategoryRepository extends JpaRepository<ToolCategory, Long> {

    /** Resolves a raw vision prediction label to its catalog row. */
    Optional<ToolCategory> findByModelLabel(String modelLabel);

    List<ToolCategory> findAllByOrderByDisplayNameAsc();

    List<ToolCategory> findByModelLabelIn(Collection<String> modelLabels);
}
