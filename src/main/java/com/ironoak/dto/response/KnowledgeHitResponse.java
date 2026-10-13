package com.ironoak.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.document.Document;

import java.util.Map;

/**
 * One knowledge-base passage returned to Piper. The text has no
 * document/section header; those are separate fields.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeHitResponse(
        String text,
        String namespace,
        @JsonProperty("doc_title") String docTitle,
        String section,
        Integer page,
        double score) {

    public static KnowledgeHitResponse from(Document document) {

        Map<String, Object> metadata = document.getMetadata();
        Double rawScore = document.getScore();
        double score = rawScore == null ? 0.0 : Math.round(rawScore * 100.0) / 100.0;

        return new KnowledgeHitResponse(
                stripHeader(document.getText()),
                asString(metadata.get("namespace")),
                asString(metadata.get("doc_title")),
                asString(metadata.get("section")),
                metadata.get("page") instanceof Number number ? number.intValue() : null,
                score);
    }

    /**
     * Chunks are stored as "Document: ...\nSection: ...\n\nbody"; the body starts
     * after the first blank line.
     */
    static String stripHeader(String text) {

        if (text == null) {
            return "";
        }
        int end = text.indexOf("\n\n");

        return text.startsWith("Document: ") && end >= 0 ? text.substring(end + 2).strip() : text.strip();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}