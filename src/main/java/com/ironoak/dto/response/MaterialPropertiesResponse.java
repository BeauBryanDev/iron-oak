package com.ironoak.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Six authoritative material properties loaded from metal_properties.json. */
public record MaterialPropertiesResponse(
        @JsonProperty("material_name") String materialName,
        double kc1,
        double mc,
        @JsonProperty("vc_ref") double vcRef,
        int hardness,
        double density,
        @JsonProperty("thermal_conductivity") double thermalConductivity) {
}
