package com.ironoak.services;

import com.ironoak.dto.response.ToolCategoryResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.mapper.ProductMapper;
import com.ironoak.repository.ToolCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ToolCategoryService {

    private final ToolCategoryRepository categories;
    private final ProductMapper mapper;

    public ToolCategoryService(ToolCategoryRepository categories,
            ProductMapper mapper) {

        this.categories = categories;
        this.mapper = mapper;
    }

    public List<ToolCategoryResponse> list() {
        return categories.findAllByOrderByDisplayNameAsc().stream()
                .map(mapper::toCategoryResponse).toList();
    }

    public ToolCategoryResponse getByModelLabel(String modelLabel) {
        return categories.findByModelLabel(modelLabel)
                .map(mapper::toCategoryResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Tool category", modelLabel));
    }
}
