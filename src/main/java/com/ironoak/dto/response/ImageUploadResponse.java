package com.ironoak.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Temporary reference returned for an image held in the in-memory chat cache. */
public record ImageUploadResponse(@JsonProperty("image_id") String imageId) {
}
