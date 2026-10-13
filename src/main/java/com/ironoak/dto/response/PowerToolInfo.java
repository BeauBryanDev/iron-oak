package com.ironoak.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Authoritative electrical and technical information for a power tool resource entry. */
public record PowerToolInfo(
        @JsonProperty("product_name") String productName,
        double voltage,
        double ampere,
        @JsonProperty("power_w") double powerWatts,
        @JsonProperty("technical_description") String technicalDescription) {
}
