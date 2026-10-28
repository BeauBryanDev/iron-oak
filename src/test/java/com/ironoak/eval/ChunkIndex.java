package com.ironoak.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * knowledge/chunks.jsonl by passage key (source#chunk_index): the document
 * titles for citation checks. The first copy of a twin passage wins, like the
 * search's de-duplication.
 */
final class ChunkIndex {

    static final Path FILE = Path.of("knowledge", "chunks.jsonl");

    record Chunk(String key, String source, String docTitle, String namespace) {
    }

    private final Map<String, Chunk> byKey = new HashMap<>();

    static ChunkIndex load(ObjectMapper json) throws IOException {

        ChunkIndex index = new ChunkIndex();
        try (BufferedReader lines = Files.newBufferedReader(FILE)) {
            String line;
            while ((line = lines.readLine()) != null) {
                JsonNode row = json.readTree(line);
                String key = row.path("source").asText() + "#" + row.path("chunk_index").asInt();
                index.byKey.putIfAbsent(key, new Chunk(key,
                        row.path("source").asText(),
                        row.path("doc_title").asText(),
                        row.path("namespace").asText()));
            }
        }
        return index;
    }

    Chunk get(String key) {
        return byKey.get(key);
    }

    Collection<Chunk> all() {
        return byKey.values();
    }
}
