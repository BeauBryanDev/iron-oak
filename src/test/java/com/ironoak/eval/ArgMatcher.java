package com.ironoak.eval;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compares the arguments Piper sent to a tool with the expected ones in the
 * golden set (matchers described in its "_schema" block).
 */
final class ArgMatcher {

    /** Per parameter: did it match. */
    record Result(Map<String, Boolean> params) {

        double accuracy() {
            return params.isEmpty()
                    ? 1.0
                    : params.values().stream().filter(b -> b).count() / (double) params.size();
        }

        boolean exact() {
            return params.values().stream().allMatch(b -> b);
        }
    }

    private ArgMatcher() {
    }

    static Result match(JsonNode expected, JsonNode actual) {

        Map<String, Boolean> params = new java.util.LinkedHashMap<>();
        if (expected == null || !expected.isObject()) {
            return new Result(params);
        }
        expected.fields().forEachRemaining(entry -> params.put(entry.getKey(),
                matches(entry.getValue(), actual == null ? null : actual.get(entry.getKey()))));
        return new Result(params);
    }

    private static boolean matches(JsonNode spec, JsonNode actual) {

        boolean missing = actual == null || actual.isNull()
                || (actual.isTextual() && actual.asText().isBlank())
                || (actual.isArray() && actual.isEmpty());

        if (!spec.isObject()) {
            return !missing && scalarEquals(spec, actual);
        }
        if (missing) {
            return spec.path("allowMissing").asBoolean(false);
        }
        if (spec.has("present")) {
            return true;
        }
        if (spec.has("equals")) {
            return scalarEquals(spec.get("equals"), actual);
        }
        if (spec.has("contains")) {
            String needle = TextNormalizer.compact(spec.get("contains").asText());
            return values(actual).stream().anyMatch(v -> TextNormalizer.compact(v).contains(needle));
        }
        if (spec.has("oneOf") || spec.has("includesAny")) {
            JsonNode options = spec.has("oneOf") ? spec.get("oneOf") : spec.get("includesAny");
            for (JsonNode option : options) {
                for (String value : values(actual)) {
                    if (TextNormalizer.compact(value).equals(TextNormalizer.compact(option.asText()))) {
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }

    private static boolean scalarEquals(JsonNode expected, JsonNode actual) {

        if (expected.isNumber()) {
            try {
                double value = actual.isNumber() ? actual.asDouble() : Double.parseDouble(actual.asText());
                return Math.abs(value - expected.asDouble()) <= 1e-6 * Math.max(1.0, Math.abs(expected.asDouble()));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return TextNormalizer.compact(actual.asText()).equals(TextNormalizer.compact(expected.asText()));
    }

    private static List<String> values(JsonNode actual) {

        List<String> values = new ArrayList<>();
        if (actual.isArray()) {
            actual.forEach(v -> values.add(v.asText()));
        } else {
            values.add(actual.asText());
        }
        return values;
    }
}
