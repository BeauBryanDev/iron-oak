package com.ironoak.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironoak.eval.GoldenSet.ExpectedTool;
import com.ironoak.eval.GoldenSet.GoldenCase;
import com.ironoak.eval.RawRun.Passage;
import com.ironoak.eval.RawRun.RawCase;
import com.ironoak.eval.RawRun.Search;
import com.ironoak.eval.RawRun.ToolCall;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a raw run into metrics.json and report.md. Everything except the judge
 * block is computed from the raw file alone (string, ID and rank based), so the
 * same raw.json always gives the same numbers; the judge reads its disk cache.
 */
final class EvalScorer {

    static final int[] KS = {3, 5, 10};
    private static final String SEARCH_TOOL = "search_knowledge";

    private final ObjectMapper json;
    private final ChunkIndex chunks;
    private final HaikuJudge judge;

    EvalScorer(ObjectMapper json, ChunkIndex chunks, HaikuJudge judge) {
        this.json = json;
        this.chunks = chunks;
        this.judge = judge;
    }

    /** Scores the run and writes metrics.json and report.md next to raw.json. */
    Map<String, Object> write(Path dir, RawRun raw, GoldenSet golden) throws IOException, InterruptedException {

        Map<String, Object> metrics = score(raw, golden);
        json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("metrics.json").toFile(), metrics);
        Files.writeString(dir.resolve("report.md"), EvalReport.markdown(metrics));
        return metrics;
    }

    Map<String, Object> score(RawRun raw, GoldenSet golden) throws IOException, InterruptedException {

        Map<String, GoldenCase> byId = golden.cases().stream()
                .collect(Collectors.toMap(GoldenCase::id, Function.identity()));
        List<Map<String, Object>> cases = new ArrayList<>();
        List<Map<String, Double>> flat = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        Map<String, Integer> topKs = new TreeMap<>();
        Map<String, Integer> namespaceChoices = new TreeMap<>();

        for (RawCase rc : raw.cases()) {
            GoldenCase gc = byId.get(rc.id());
            if (gc == null) {
                continue;
            }
            Map<String, Object> scored = scoreCase(rc, gc);
            cases.add(scored);
            flat.add(flatten(scored));
            categories.add(gc.category());
            for (Search s : rc.agentSearches()) {
                topKs.merge(String.valueOf(s.topK()), 1, Integer::sum);
                String ns = s.namespaces().size() == chunksNamespaces() ? "(all)" : String.join("+", s.namespaces());
                namespaceChoices.merge(ns, 1, Integer::sum);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("label", raw.label());
        run.put("started_at", raw.startedAt());
        run.put("git_commit", raw.gitCommit());
        run.put("chat_model", raw.chatModel());
        run.put("embedding_model", raw.embeddingModel());
        run.put("temperature", raw.temperature());
        run.put("seed", raw.seed());
        run.put("judge_model", judge == null ? null : judge.model());
        run.put("cases", cases.size());
        run.put("rag_cases", cases.stream().filter(c -> Boolean.TRUE.equals(c.get("rag"))).count());
        out.put("run", run);
        out.put("overall", aggregate(flat));

        Map<String, Object> byCategory = new TreeMap<>();
        for (String category : new LinkedHashSet<>(categories)) {
            List<Map<String, Double>> group = new ArrayList<>();
            for (int i = 0; i < flat.size(); i++) {
                if (categories.get(i).equals(category)) {
                    group.add(flat.get(i));
                }
            }
            byCategory.put(category, aggregate(group));
        }
        out.put("by_category", byCategory);
        out.put("agent_search_top_k", topKs);
        out.put("agent_search_namespaces", namespaceChoices);
        out.put("cases", cases);
        return out;
    }

    private Map<String, Object> scoreCase(RawCase rc, GoldenCase gc) throws IOException, InterruptedException {

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", gc.id());
        c.put("category", gc.category());
        c.put("rag", gc.isRag());
        c.put("question", rc.question());
        c.put("answer", rc.answer());
        c.put("error", rc.error());
        c.put("tools_called", rc.toolCalls().stream().map(ToolCall::name).toList());
        c.put("run", map("latency_ms", (double) rc.latencyMs(),
                "prompt_tokens", rc.promptTokens() == null ? null : rc.promptTokens().doubleValue(),
                "completion_tokens", rc.completionTokens() == null ? null : rc.completionTokens().doubleValue(),
                "failed", rc.error() == null ? 0.0 : 1.0));
        c.put("tools", toolMetrics(rc, gc));

        if (!gc.isRag()) {
            return c;
        }
        Map<String, Integer> labelled = gc.relevant().stream()
                .collect(Collectors.toMap(GoldenSet.Relevance::key, GoldenSet.Relevance::grade, Math::max));
        List<Integer> labelledGrades = new ArrayList<>(labelled.values());
        List<String> evidence = gc.evidence().stream().map(TextNormalizer::normalize).toList();

        List<Passage> contexts = contexts(rc.agentSearches());
        List<Integer> agentGrades = contexts.stream().map(p -> grade(p, labelled, evidence)).toList();
        List<String> agentKeys = contexts.stream().map(Passage::key).toList();
        Map<String, Object> agent = rankMetrics(agentKeys, agentGrades, labelled, labelledGrades);
        agent.put("searched", rc.agentSearches().isEmpty() ? 0.0 : 1.0);
        agent.put("search_calls", (double) rc.agentSearches().size());
        agent.put("contexts", (double) contexts.size());
        agent.put("top_k", rc.agentSearches().isEmpty() ? null
                : rc.agentSearches().stream().mapToInt(Search::topK).average().orElse(0));
        agent.put("namespace_ok", namespaceOk(rc.agentSearches(), gc));
        c.put("agent_retrieval", agent);

        List<Passage> alone = rc.retrieverAlone() == null ? List.of() : rc.retrieverAlone();
        List<Integer> aloneGrades = alone.stream().map(p -> grade(p, labelled, evidence)).toList();
        c.put("retriever", rankMetrics(alone.stream().map(Passage::key).toList(), aloneGrades, labelled, labelledGrades));
        c.put("embedding", embeddingMetrics(alone, aloneGrades));

        String answer = TextNormalizer.normalize(rc.answer());
        String contextText = TextNormalizer.normalize(contexts.stream().map(Passage::text).collect(Collectors.joining("\n")));
        Map<String, Object> gen = new LinkedHashMap<>();
        gen.put("context_precision", contexts.isEmpty() ? 0.0 : RetrievalMetrics.averagePrecision(agentGrades));
        gen.put("context_recall", factShare(contextText, gc.keyFacts()));
        gen.put("answer_correctness", factShare(answer, gc.keyFacts()));
        gen.putAll(citations(rc.answer(), contexts, labelled.keySet()));
        c.put("generation", gen);
        c.put("judge", judgeMetrics(rc, gc, contexts));
        c.put("gold_rank_retriever", firstRelevantRank(aloneGrades));
        c.put("gold_rank_agent", firstRelevantRank(agentGrades));
        return c;
    }

    // tools

    private Map<String, Object> toolMetrics(RawCase rc, GoldenCase gc) throws IOException {

        List<ToolCall> calls = rc.toolCalls();
        Set<String> called = calls.stream().map(ToolCall::name).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> satisfied = new HashSet<>();
        boolean allMatched = true;
        Double argAccuracy = null;
        Boolean argExact = null;
        List<Map<String, Boolean>> argDetail = new ArrayList<>();

        for (ExpectedTool expected : gc.expectedTools()) {
            List<String> hits = expected.names().stream().filter(called::contains).toList();
            if (hits.isEmpty()) {
                allMatched = false;
                continue;
            }
            satisfied.addAll(hits);
            String primary = expected.names().get(0);
            if (expected.args() != null && called.contains(primary)) {
                ArgMatcher.Result best = null;
                for (ToolCall call : calls) {
                    if (!call.name().equals(primary)) {
                        continue;
                    }
                    ArgMatcher.Result result = ArgMatcher.match(expected.args(), parse(call.input()));
                    if (best == null || result.accuracy() > best.accuracy()) {
                        best = result;
                    }
                }
                argAccuracy = argAccuracy == null ? best.accuracy() : Math.min(argAccuracy, best.accuracy());
                argExact = (argExact == null || argExact) && best.exact();
                argDetail.add(best.params());
            }
        }
        boolean forbidden = gc.expectNoTools()
                ? !calls.isEmpty()
                : calls.stream().anyMatch(call -> gc.forbiddenTools().contains(call.name()));
        Set<String> extra = new LinkedHashSet<>(called);
        extra.removeAll(satisfied);
        gc.optionalTools().forEach(extra::remove);
        boolean selection = gc.expectNoTools() ? calls.isEmpty() : allMatched;
        Double firstTool = gc.expectedTools().isEmpty() ? null
                : !calls.isEmpty() && gc.expectedTools().get(0).names().contains(calls.get(0).name()) ? 1.0 : 0.0;

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("selection", selection ? 1.0 : 0.0);
        t.put("exact_set", selection && extra.isEmpty() ? 1.0 : 0.0);
        t.put("first_tool", firstTool);
        t.put("forbidden_violation", forbidden ? 1.0 : 0.0);
        t.put("arg_accuracy", argAccuracy);
        t.put("arg_exact", argExact == null ? null : argExact ? 1.0 : 0.0);
        t.put("errors", (double) calls.stream().filter(call -> call.error() != null).count());
        t.put("calls", (double) calls.size());
        t.put("pass", selection && !forbidden && !Boolean.FALSE.equals(argExact) ? 1.0 : 0.0);
        t.put("extra_tools", new ArrayList<>(extra));
        t.put("arg_detail", argDetail);
        return t;
    }

    private Double namespaceOk(List<Search> searches, GoldenCase gc) {

        if (searches.isEmpty()) {
            return null;
        }
        JsonNode expected = gc.expectedTools().stream()
                .filter(e -> e.names().get(0).equals(SEARCH_TOOL) && e.args() != null)
                .map(e -> e.args().path("namespaces").path("includesAny"))
                .findFirst().orElse(null);
        if (expected == null || expected.isMissingNode()) {
            return null;
        }
        Set<String> wanted = new HashSet<>();
        expected.forEach(n -> wanted.add(n.asText()));
        return searches.stream().anyMatch(s -> s.namespaces().stream().anyMatch(wanted::contains)) ? 1.0 : 0.0;
    }

    // retrieval

    /** Piper's passages across all its searches, in call order, first copy of each passage only. */
    private static List<Passage> contexts(List<Search> searches) {

        Map<String, Passage> unique = new LinkedHashMap<>();
        for (Search search : searches) {
            for (Passage passage : search.passages()) {
                unique.putIfAbsent(passage.key(), passage);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static int grade(Passage passage, Map<String, Integer> labelled, List<String> evidence) {

        Integer grade = labelled.get(passage.key());
        if (grade != null) {
            return grade;
        }
        if (evidence.isEmpty()) {
            return 0;
        }
        String text = TextNormalizer.normalize(passage.text());
        return evidence.stream().allMatch(text::contains) ? 1 : 0;
    }

    private static Map<String, Object> rankMetrics(List<String> keys,
            List<Integer> grades,
            Map<String, Integer> labelled,
            List<Integer> labelledGrades) {

        Map<String, Object> m = new LinkedHashMap<>();
        for (int k : KS) {
            m.put("recall@" + k, RetrievalMetrics.recallAtK(keys, labelled, k));
            m.put("precision@" + k, RetrievalMetrics.precisionAtK(grades, k));
            m.put("hit@" + k, RetrievalMetrics.hitAtK(grades, k));
            m.put("ndcg@" + k, RetrievalMetrics.ndcgAtK(grades, labelledGrades, k));
        }
        m.put("mrr", RetrievalMetrics.reciprocalRank(grades.subList(0, Math.min(10, grades.size()))));
        return m;
    }

    private static Map<String, Object> embeddingMetrics(List<Passage> alone, List<Integer> grades) {

        Double goldScore = null;
        Double otherScore = null;
        Integer goldRank = null;
        for (int i = 0; i < alone.size(); i++) {
            if (grades.get(i) > 0 && goldScore == null) {
                goldScore = alone.get(i).score();
                goldRank = i + 1;
            } else if (grades.get(i) == 0 && otherScore == null) {
                otherScore = alone.get(i).score();
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("gold_in_top10", goldScore == null ? 0.0 : 1.0);
        m.put("gold_rank", goldRank == null ? null : goldRank.doubleValue());
        m.put("gold_score", goldScore);
        m.put("best_other_score", otherScore);
        m.put("top1_score", alone.isEmpty() ? null : alone.get(0).score());
        m.put("margin", goldScore == null || otherScore == null ? null : goldScore - otherScore);
        return m;
    }

    private static Double firstRelevantRank(List<Integer> grades) {
        for (int i = 0; i < grades.size(); i++) {
            if (grades.get(i) > 0) {
                return (double) (i + 1);
            }
        }
        return null;
    }

    // generation

    private static Double factShare(String normalizedText, List<List<String>> facts) {
        if (facts.isEmpty()) {
            return null;
        }
        long found = facts.stream().filter(f -> TextNormalizer.containsFact(normalizedText, f)).count();
        return (double) found / facts.size();
    }

    /**
     * A document counts as cited when the answer names its title or its file name
     * (letters and digits only, so "Iron Oak Business Rules" matches "Iron & Oak
     * Business Rules").
     */
    private Map<String, Object> citations(String answer, List<Passage> contexts, Set<String> goldKeys) {

        String compactAnswer = TextNormalizer.compact(answer);
        Set<String> retrievedDocs = contexts.stream().map(p -> source(p.key())).collect(Collectors.toSet());
        Set<String> goldDocs = goldKeys.stream().map(EvalScorer::source).collect(Collectors.toSet());

        Set<String> cited = new HashSet<>();
        Map<String, Set<String>> namesBySource = new TreeMap<>();
        for (ChunkIndex.Chunk chunk : chunks.all()) {
            namesBySource.computeIfAbsent(chunk.source(), s -> new HashSet<>()).add(chunk.docTitle());
        }
        namesBySource.forEach((src, titles) -> {
            Set<String> names = new HashSet<>(titles);
            names.add(src.replaceFirst("\\.md$", ""));
            for (String name : names) {
                String compact = TextNormalizer.compact(name);
                if (compact.length() >= 6 && compactAnswer.contains(compact)) {
                    cited.add(src);
                }
            }
        });
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cites_retrieved", contexts.isEmpty() ? null : cited.stream().anyMatch(retrievedDocs::contains) ? 1.0 : 0.0);
        m.put("cites_gold_doc", cited.stream().anyMatch(goldDocs::contains) ? 1.0 : 0.0);
        m.put("unsupported_citations", (double) cited.stream().filter(s -> !retrievedDocs.contains(s)).count());
        m.put("cited_docs", new ArrayList<>(new java.util.TreeSet<>(cited)));
        return m;
    }

    private Map<String, Object> judgeMetrics(RawCase rc, GoldenCase gc, List<Passage> contexts)
            throws IOException, InterruptedException {

        Map<String, Object> m = new LinkedHashMap<>();
        if (judge == null || rc.answer() == null) {
            m.put("status", judge == null ? "pending judge" : "no answer");
            return m;
        }
        HaikuJudge.Verdict verdict = judge.judge(rc.question(), rc.answer(), contexts, gc.referenceAnswer(), gc.keyFacts());
        m.put("status", "judged");
        m.put("faithfulness", verdict.claims().isEmpty() ? null
                : verdict.claims().stream().filter(b -> b).count() / (double) verdict.claims().size());
        int facts = Math.min(verdict.facts().size(), gc.keyFacts().size());
        m.put("answer_correctness", facts == 0 ? null
                : verdict.facts().subList(0, facts).stream().filter(b -> b).count() / (double) facts);
        m.put("answer_relevancy", judge.answerRelevancy(rc.question(), verdict.questions()));
        m.put("claims", (double) verdict.claims().size());
        return m;
    }

    // helpers

    private int chunksNamespaces() {
        return (int) chunks.all().stream().map(ChunkIndex.Chunk::namespace).distinct().count();
    }

    private static String source(String key) {
        int hash = key.lastIndexOf('#');
        return hash < 0 ? key : key.substring(0, hash);
    }

    private JsonNode parse(String input) throws IOException {
        return input == null || input.isBlank() ? json.createObjectNode() : json.readTree(input);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    /** group.metric -> value, numbers only (lists, strings and nulls are skipped). */
    @SuppressWarnings("unchecked")
    private static Map<String, Double> flatten(Map<String, Object> scored) {

        Map<String, Double> flat = new LinkedHashMap<>();
        for (Map.Entry<String, Object> group : scored.entrySet()) {
            if (group.getValue() instanceof Map<?, ?> values) {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) values).entrySet()) {
                    if (e.getValue() instanceof Number n) {
                        flat.put(group.getKey() + "." + e.getKey(), n.doubleValue());
                    }
                }
            }
        }
        return flat;
    }

    /** Mean of every metric over the cases that have it, with n; rounded for stable files. */
    private static Map<String, Object> aggregate(List<Map<String, Double>> rows) {

        Map<String, double[]> sums = new LinkedHashMap<>();
        for (Map<String, Double> row : rows) {
            row.forEach((k, v) -> {
                double[] s = sums.computeIfAbsent(k, x -> new double[2]);
                s[0] += v;
                s[1]++;
            });
        }
        Map<String, Object> out = new LinkedHashMap<>();
        sums.forEach((k, s) -> out.put(k, map("mean", round(s[0] / s[1]), "n", (int) s[1])));
        return out;
    }

    static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
