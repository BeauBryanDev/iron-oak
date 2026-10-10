package com.ironoak.services;

import com.ironoak.domain.enums.AuditAction;
import com.ironoak.domain.ToolCategory;
import com.ironoak.dto.request.UpdateToolCategoryRequest;
import com.ironoak.dto.response.ToolCategoryResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.ProductMapper;
import com.ironoak.repository.ToolCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/**
 * Staff edits to a tool category's display text. Categories are not created or
 * deleted
 * here: each one mirrors a class of the vision model, and modelLabel never
 * changes.
 */
@Service
@Transactional
public class AdminToolCategoryService {

    private final ToolCategoryRepository toolCategories;
    private final ProductMapper mapper;
    private final AuditService audit;

    public AdminToolCategoryService(ToolCategoryRepository toolCategories,
            ProductMapper mapper,
            AuditService audit) {

        this.toolCategories = toolCategories;
        this.mapper = mapper;
        this.audit = audit;
    }

    public ToolCategoryResponse update(Long id, UpdateToolCategoryRequest request) {

        ToolCategory category = toolCategories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tool category", id));

        ToolCategoryResponse before = mapper.toCategoryResponse(category);
        category.setDisplayName(request.displayName().trim());

        category.setSynonyms(request.synonyms() == null ? new ArrayList<>()
                : new ArrayList<>(request.synonyms().stream().map(String::trim).toList()));

        category.setDescription(AdminProductService.blankToNull(request.description()));

        toolCategories.saveAndFlush(category);

        ToolCategoryResponse after = mapper.toCategoryResponse(category);
        audit.record(AuditAction.TOOL_CATEGORY_UPDATE, "TOOL_CATEGORY",
                id, before, after);

        return after;
    }
}
