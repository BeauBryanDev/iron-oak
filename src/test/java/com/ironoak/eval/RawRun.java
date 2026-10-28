package com.ironoak.eval;

import java.util.List;
import java.util.Map;

/**
 * Everything one eval run observed, written to eval/results/{label}/raw.json.
 * Scoring reads only this file, so a run can be re-scored (e.g. once the judge
 * key exists) without calling Piper again.
 */
record RawRun(
        String label,
        String startedAt,
        String gitCommit,
        String chatModel,
        String embeddingModel,
        Double temperature,
        Integer seed,
        /** {ORDER_NUMBER} etc. as filled in for this run, so re-scoring loads the same golden set */
        Map<String, String> placeholders,
        List<RawCase> cases) {

    record RawCase(
            String id,
            String category,
            String question,
            String answer,
            String error,
            long latencyMs,
            Integer promptTokens,
            Integer completionTokens,
            List<ToolCall> toolCalls,
            List<Search> agentSearches,
            List<Passage> retrieverAlone) {
    }

    /** One tool call Piper made; output is cut at OUTPUT_LIMIT characters. */
    record ToolCall(String name, String input, String output, String error, long latencyMs) {

        static final int OUTPUT_LIMIT = 2000;
    }

    /** One knowledge search Piper's search_knowledge made, with what came back. */
    record Search(String query, List<String> namespaces, int topK, List<Passage> passages) {
    }

    record Passage(
            String key,
            String id,
            String namespace,
            String docTitle,
            String section,
            Integer page,
            double score,
            String text) {
    }
}
