package com.ironoak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/** One customer message to Piper. */
public record ChatMessageRequest(
        @NotBlank @Size(max = 2000) String content,
        @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9-]+") String image_id) {
}
