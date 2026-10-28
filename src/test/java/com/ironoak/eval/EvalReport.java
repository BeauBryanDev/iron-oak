package com.ironoak.eval;

import java.util.List;
import java.util.Map;

/** report.md for one run, built from its metrics map. */
final class EvalReport {

    private static final int ANSWER_CHARS = 400;

    private EvalReport() {
    }

    @SuppressWarnings("unchecked")
    static String markdown(Map<String, Object> metrics) {

        Map<String, Object> run = (Map<String, Object>) metrics.get("run");
        Map<String, Object> overall = (Map<String, Object>) metrics.get("overall");
        Map<String, Object> byCategory = (Map<String, Object>) metrics.get("by_category");
        List<Map<String, Object>> cases = (List<Map<String, Object>>) metrics.get("cases");
        StringBuilder md = new StringBuilder();

        md.append("# Piper eval: ").append(run.get("label")).append("\n\n");
        md.append("Commit `").append(run.get("git_commit")).append("`, chat model `").append(run.get("chat_model"))
                .append("` (temperature ").append(run.get("temperature")).append(", seed ").append(run.get("seed"))
                .append("), embeddings `").append(run.get("embedding_model")).append("`, judge `")
                .append(run.get("judge_model") == null ? "pending" : run.get("judge_model")).append("`. ")
                .append(run.get("cases")).append(" cases (").append(run.get("rag_cases")).append(" RAG), started ")
                .append(run.get("started_at")).append(".\n\n");

        md.append("## Summary\n\n| Metric | Value | n |\n|---|---|---|\n");
        row(md, overall, "Tool pass (right tools, no forbidden call, exact args)", "tools.pass");
        row(md, overall, "Tool selection accuracy", "tools.selection");
        row(md, overall, "Exact tool-set match", "tools.exact_set");
        row(md, overall, "First tool correct", "tools.first_tool");
        row(md, overall, "Argument accuracy", "tools.arg_accuracy");
        row(md, overall, "Argument exact match", "tools.arg_exact");
        row(md, overall, "Forbidden-tool violations", "tools.forbidden_violation");
        row(md, overall, "Tool errors per case", "tools.errors");
        row(md, overall, "Retriever Hit@5", "retriever.hit@5");
        row(md, overall, "Retriever MRR", "retriever.mrr");
        row(md, overall, "Retriever NDCG@10", "retriever.ndcg@10");
        row(md, overall, "Agent searched the knowledge base", "agent_retrieval.searched");
        row(md, overall, "Agent namespace choice correct", "agent_retrieval.namespace_ok");
        row(md, overall, "Agent Hit@5", "agent_retrieval.hit@5");
        row(md, overall, "Context precision", "generation.context_precision");
        row(md, overall, "Context recall (key facts in contexts)", "generation.context_recall");
        row(md, overall, "Answer correctness (key facts in answer)", "generation.answer_correctness");
        row(md, overall, "Cites a retrieved document", "generation.cites_retrieved");
        row(md, overall, "Cites the gold document", "generation.cites_gold_doc");
        row(md, overall, "Unsupported citations per case", "generation.unsupported_citations");
        row(md, overall, "Faithfulness (judge)", "judge.faithfulness");
        row(md, overall, "Answer relevancy (judge + embeddings)", "judge.answer_relevancy");
        row(md, overall, "Answer correctness (judge)", "judge.answer_correctness");
        row(md, overall, "Mean latency (ms)", "run.latency_ms");
        row(md, overall, "Mean prompt tokens", "run.prompt_tokens");
        row(md, overall, "Mean completion tokens", "run.completion_tokens");

        md.append("\n## Retrieval at K\n\nRetriever = the raw question against all namespaces (top 10). ")
                .append("Agent = the passages Piper's own search_knowledge calls returned, in order.\n\n");
        md.append("| Path | K | Recall | Precision | Hit rate | NDCG |\n|---|---|---|---|---|---|\n");
        for (String path : List.of("retriever", "agent_retrieval")) {
            for (int k : EvalScorer.KS) {
                md.append("| ").append(path.equals("retriever") ? "Retriever" : "Agent").append(" | ").append(k)
                        .append(" | ").append(mean(overall, path + ".recall@" + k))
                        .append(" | ").append(mean(overall, path + ".precision@" + k))
                        .append(" | ").append(mean(overall, path + ".hit@" + k))
                        .append(" | ").append(mean(overall, path + ".ndcg@" + k)).append(" |\n");
            }
        }
        md.append("\nMRR: retriever ").append(mean(overall, "retriever.mrr"))
                .append(", agent ").append(mean(overall, "agent_retrieval.mrr")).append(".\n");

        md.append("\n## Embeddings (retriever path)\n\n| Metric | Value |\n|---|---|\n");
        md.append("| Relevant passage in top 10 | ").append(mean(overall, "embedding.gold_in_top10")).append(" |\n");
        md.append("| Rank of first relevant passage (when found) | ").append(mean(overall, "embedding.gold_rank")).append(" |\n");
        md.append("| Cosine of first relevant passage | ").append(mean(overall, "embedding.gold_score")).append(" |\n");
        md.append("| Cosine of best non-relevant passage | ").append(mean(overall, "embedding.best_other_score")).append(" |\n");
        md.append("| Margin (relevant - best non-relevant) | ").append(mean(overall, "embedding.margin")).append(" |\n");

        md.append("\n## Agent search behaviour\n\n")
                .append("top_k chosen: ").append(metrics.get("agent_search_top_k"))
                .append("  \nnamespaces chosen: ").append(metrics.get("agent_search_namespaces"))
                .append("  \nmean top_k: ").append(mean(overall, "agent_retrieval.top_k"))
                .append(", mean searches per RAG case: ").append(mean(overall, "agent_retrieval.search_calls")).append("\n");

        md.append("\n## By category\n\n| Category | Tool pass | Retriever Hit@5 | Retriever MRR | Agent Hit@5 | ")
                .append("Context recall | Answer correctness | Faithfulness |\n|---|---|---|---|---|---|---|---|\n");
        byCategory.forEach((category, value) -> {
            Map<String, Object> m = (Map<String, Object>) value;
            md.append("| ").append(category)
                    .append(" | ").append(mean(m, "tools.pass"))
                    .append(" | ").append(mean(m, "retriever.hit@5"))
                    .append(" | ").append(mean(m, "retriever.mrr"))
                    .append(" | ").append(mean(m, "agent_retrieval.hit@5"))
                    .append(" | ").append(mean(m, "generation.context_recall"))
                    .append(" | ").append(mean(m, "generation.answer_correctness"))
                    .append(" | ").append(mean(m, "judge.faithfulness")).append(" |\n");
        });

        md.append("\n## Cases\n\n| Case | Tools called | Tool pass | Gold rank (retriever / agent) | Facts in answer |\n")
                .append("|---|---|---|---|---|\n");
        for (Map<String, Object> c : cases) {
            Map<String, Object> tools = (Map<String, Object>) c.get("tools");
            Map<String, Object> gen = (Map<String, Object>) c.get("generation");
            md.append("| ").append(c.get("id"))
                    .append(" | ").append(String.join(", ", (List<String>) c.get("tools_called")))
                    .append(" | ").append(fmt(tools.get("pass")))
                    .append(" | ").append(Boolean.TRUE.equals(c.get("rag"))
                            ? fmt(c.get("gold_rank_retriever")) + " / " + fmt(c.get("gold_rank_agent")) : "")
                    .append(" | ").append(gen == null ? "" : fmt(gen.get("answer_correctness"))).append(" |\n");
        }

        md.append("\n## Failures\n\nCases with a tool failure, no relevant passage in Piper's contexts, ")
                .append("or fewer than all key facts in the answer.\n");
        for (Map<String, Object> c : cases) {
            Map<String, Object> tools = (Map<String, Object>) c.get("tools");
            Map<String, Object> gen = (Map<String, Object>) c.get("generation");
            boolean toolFail = ((Number) tools.get("pass")).doubleValue() < 1.0;
            boolean ragFail = Boolean.TRUE.equals(c.get("rag"))
                    && (c.get("gold_rank_agent") == null
                    || (gen.get("answer_correctness") instanceof Number n && n.doubleValue() < 1.0));
            if (!toolFail && !ragFail && c.get("error") == null) {
                continue;
            }
            md.append("\n### ").append(c.get("id")).append("\n\n");
            md.append("**Q:** ").append(c.get("question")).append("  \n");
            md.append("**Tools:** ").append(c.get("tools_called"));
            if (toolFail) {
                md.append(" (extra: ").append(tools.get("extra_tools")).append(", args: ").append(tools.get("arg_detail")).append(")");
            }
            md.append("  \n");
            if (c.get("error") != null) {
                md.append("**Error:** ").append(c.get("error")).append("  \n");
            }
            String answer = c.get("answer") == null ? "" : c.get("answer").toString().replace("\n", " ");
            md.append("**A:** ").append(answer.length() > ANSWER_CHARS ? answer.substring(0, ANSWER_CHARS) + "…" : answer)
                    .append("\n");
        }
        return md.toString();
    }

    private static void row(StringBuilder md, Map<String, Object> overall, String label, String path) {
        md.append("| ").append(label).append(" | ").append(mean(overall, path)).append(" | ")
                .append(count(overall, path)).append(" |\n");
    }

    @SuppressWarnings("unchecked")
    private static String mean(Map<String, Object> group, String path) {
        Object value = group.get(path);
        return value instanceof Map<?, ?> m ? fmt(((Map<String, Object>) m).get("mean")) : "–";
    }

    @SuppressWarnings("unchecked")
    private static String count(Map<String, Object> group, String path) {
        Object value = group.get(path);
        return value instanceof Map<?, ?> m ? String.valueOf(((Map<String, Object>) m).get("n")) : "0";
    }

    private static String fmt(Object value) {
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && Math.abs(d) >= 1 ? String.valueOf((long) d) : String.format(java.util.Locale.ROOT, "%.3f", d);
        }
        return value == null ? "–" : value.toString();
    }
}
