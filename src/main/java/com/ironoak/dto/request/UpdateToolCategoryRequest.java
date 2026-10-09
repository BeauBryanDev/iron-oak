package com.ironoak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** modelLabel is not editable: it is the CNN class name the vision model emits. */
public record UpdateToolCategoryRequest(
        @NotBlank @Size(max = 150) String displayName,
        List<@NotBlank @Size(max = 100) String> synonyms,
        String description) {
}
