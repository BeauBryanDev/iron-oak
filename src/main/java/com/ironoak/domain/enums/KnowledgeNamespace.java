package com.ironoak.domain.enums;

import com.ironoak.exceptions.BusinessRuleException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Namespaces of the Milvus knowledge base; value is the metadata.namespace
 * stored with each chunk.
 */
public enum KnowledgeNamespace {

    IRON_OAK("iron_oak"),
    HOME_DO_IT_YOURSELF("home_do_it_yourself"),
    MACHINE_OPERATIONS("machine_operations"),
    MACHINE_MAINTENANCE("machine_maintenance"),
    POWER_TOOLS("power_tools");

    private final String value;

    KnowledgeNamespace(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static KnowledgeNamespace fromValue(String raw) {

        if (raw != null) {
            for (KnowledgeNamespace namespace : values()) {
                if (namespace.value.equalsIgnoreCase(raw.strip())) {
                    return namespace;
                }
            }
        }
        String allowed = Arrays.stream(values())
                .map(KnowledgeNamespace::value)
                .collect(Collectors.joining(", "));
        throw new BusinessRuleException("Unknown namespace '" + raw + "'. Allowed: " + allowed);
    }
}