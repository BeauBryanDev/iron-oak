package com.ironoak.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.dto.response.PowerToolInfo;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only access to the detailed power-tool reference resource. */
@Service
public class PowerToolInfoService {

    private static final String RESOURCE = "Power_Tools.json";

    private final Map<String, PowerToolInfo> tools;

    public PowerToolInfoService(ObjectMapper mapper) {

        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {

            JsonNode root = mapper.readTree(input);

            if (!root.isObject()) {
                throw new IllegalStateException(RESOURCE + " must contain a JSON object");
            }

            Map<String, PowerToolInfo> loaded = new LinkedHashMap<>();

            root.fields().forEachRemaining(entry -> loaded.put(

                    entry.getKey(), fromJson(entry.getKey(), entry.getValue())));

            this.tools = Map.copyOf(loaded);

        } catch (IOException e) {

            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }

    public List<PowerToolInfo> search(String productName) {

        String query = normalize(productName);

        if (query.isBlank()) {
            return List.of();
        }

        PowerToolInfo exact = tools.get(query);

        if (exact != null) {

            return List.of(exact);
        }

        return tools.entrySet().stream()
                .filter(entry -> entry.getKey().contains(query))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private static PowerToolInfo fromJson(String name, JsonNode node) {
        return new PowerToolInfo(
                name,
                node.path("voltage").asDouble(),
                node.path("ampere").asDouble(),
                node.path("Power").asDouble(),
                node.path("technical_description").asText());
    }

    private static String normalize(String value) {
        return value == null ? ""
                : value.strip().toLowerCase(Locale.ROOT)
                        .replaceAll("[\\s-]+", "_");
    }
}
