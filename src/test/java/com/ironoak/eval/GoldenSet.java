package com.ironoak.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** eval/golden_set.json; the field meanings are in its "_schema" block. */
@JsonIgnoreProperties(ignoreUnknown = true)
record GoldenSet(List<GoldenCase> cases) {

    static final Path FILE = Path.of("eval", "golden_set.json");

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoldenCase(
            String id,
            String category,
            String question,
            List<Turn> history,
            @JsonProperty("expected_tools") List<ExpectedTool> expectedTools,
            @JsonProperty("optional_tools") List<String> optionalTools,
            @JsonProperty("forbidden_tools") List<String> forbiddenTools,
            @JsonProperty("expect_no_tools") boolean expectNoTools,
            List<Relevance> relevant,
            List<String> evidence,
            @JsonProperty("key_facts") List<List<String>> keyFacts,
            @JsonProperty("reference_answer") String referenceAnswer) {

        GoldenCase {
            history = history == null ? List.of() : history;
            expectedTools = expectedTools == null ? List.of() : expectedTools;
            optionalTools = optionalTools == null ? List.of() : optionalTools;
            forbiddenTools = forbiddenTools == null ? List.of() : forbiddenTools;
            relevant = relevant == null ? List.of() : relevant;
            evidence = evidence == null ? List.of() : evidence;
            keyFacts = keyFacts == null ? List.of() : keyFacts;
        }

        /** RAG cases are the ones with labelled passages. */
        boolean isRag() {
            return !relevant.isEmpty();
        }
    }

    record Turn(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpectedTool(List<String> names, JsonNode args) {
    }

    record Relevance(String key, int grade) {
    }

    /** Reads the file and fills the {PLACEHOLDERS} before parsing. */
    static GoldenSet load(ObjectMapper json,
            Map<String, String> placeholders) throws IOException {

        String text = Files.readString(FILE);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return json.readValue(text, GoldenSet.class);
    }
}
