package com.ironoak.dto.response;

import java.util.List;

public record ToolCategoryResponse(
        Long id,
        String modelLabel,
        String displayName,
        List<String> synonyms,
        String description) {
}
