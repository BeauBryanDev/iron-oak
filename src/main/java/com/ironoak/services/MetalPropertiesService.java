package com.ironoak.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.dto.response.MaterialPropertiesResponse;
import com.ironoak.exceptions.ResourceNotFoundException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only access to the bundled CNC material-properties reference data. */
@Service
public class MetalPropertiesService {

    private static final String RESOURCE = "metal_properties.json";

    private final Map<String, MaterialPropertiesResponse> materials;

    public MetalPropertiesService(ObjectMapper mapper) {

        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {

            JsonNode root = mapper.readTree(input);
            JsonNode materialNodes = root.path("materials");

            if (!materialNodes.isObject()) {

                throw new IllegalStateException(RESOURCE + " has no materials object");
            }

            Map<String, MaterialPropertiesResponse> loaded = new java.util.LinkedHashMap<>();
            materialNodes.fields().forEachRemaining(entry -> loaded.put(
                    entry.getKey(),
                    fromJson(entry.getKey(), entry.getValue())));

            this.materials = Map.copyOf(loaded);

        } catch (IOException e) {

            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }

    public List<MaterialPropertiesResponse> search(String materialName) {
        String query = normalize(materialName);
        if (query.isBlank()) {
            throw new ResourceNotFoundException("Material", materialName);
        }

        MaterialPropertiesResponse exact = materials.get(query);
        if (exact != null) {
            return List.of(exact);
        }

        return materials.entrySet().stream()
                .filter(entry -> entry.getKey().contains(query))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private static MaterialPropertiesResponse fromJson(String name, JsonNode node) {
        return new MaterialPropertiesResponse(
                name,
                node.path("kc1").asDouble(),
                node.path("mc").asDouble(),
                node.path("vc_ref").asDouble(),
                node.path("hardness").asInt(),
                node.path("density").asDouble(),
                node.path("thermal_conductivity").asDouble());
    }

    private static String normalize(String value) {

        return value == null ? ""
                : value.strip().toLowerCase(Locale.ROOT)
                        .replaceAll("[\\s-]+", "_");
    }
}
