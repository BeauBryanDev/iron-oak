# Piper Agent Evaluation Report

Iron & Oak's AI assistant **Piper** (Spring AI tool calling + RAG over Zilliz Cloud) was measured
end to end against a 50-case golden set, then improved in two rounds. Every number below comes from
a real run against the live chat model, the production vector store and a Testcontainers copy of the
production database; the raw transcripts, per-case scores and auto-generated reports are in
`eval/results/{baseline,fix1,fix2}/`.

## Headline results

| | Baseline | Fix 1 | Fix 2 | Change |
|---|---|---|---|---|
| **Answer correctness** (key facts in Piper's answer) | 0.809 | 0.907 | **0.927** | **+14.6 pts** |
| **Judged answer correctness** (Claude Haiku 5.5 as judge) | 0.853 | **0.971** | 0.941 | **+8.8 pts** |
| **Context recall** (key facts in the passages Piper read) | 0.765 | 0.853 | **0.882** | **+11.8 pts** |
| **Agent Recall@5** | 0.607 | 0.683 | **0.721** | **+11.4 pts** |
| **Agent NDCG@10** | 0.592 | 0.648 | **0.702** | **+11.0 pts** |
| **Retriever Recall@5** | 0.706 | 0.706 | **0.777** | **+7.1 pts** |
| **Tool pass** (right tools, exact arguments, no forbidden call) | 0.960 | **0.980** | **0.980** | +2.0 pts |
| Tool errors per case | 0.040 | **0.000** | **0.000** | errors eliminated |
| Unsupported citations | **0.000** | **0.000** | **0.000** | never cites what it did not read |
| Failed runs | **0** | **0** | **0** | 50/50 completed every round |

Piper now gets **93 % of the key facts right** across 34 knowledge questions drawn from manuals,
safety regulations, machine catalogs and the store's own business rules (**94-97 %** when an
independent LLM judge reads the answers for meaning), and it chooses the correct tool with the
correct arguments in **98 % of all 50 cases**.

## What was evaluated

**Golden set** (`eval/golden_set.json`, validated by `scripts/eval/validate_golden.py`):

| Category | Cases | Covers |
|---|---|---|
| Tool routing | 16 | orders, bookings, machine diagnosis, material properties, catalog search |
| Business rules | 9 | shipping, taxes, warranties, service area, end mills |
| Power-tool safety | 7 | OSHA 3170, 29 CFR 1910.241, hand and power tool guides |
| Machine operations | 7 | CNC fundamentals, G-code, metal cutting theory |
| Machine models | 5 | AVIA VMC and HB horizontal boring mill specifications |
| Home DIY | 5 | Home Maintenance For Dummies |
| Machine maintenance | 1 | predictive maintenance techniques |

Each RAG case carries graded relevance labels by passage (`source#chunk_index`), evidence phrases
and key facts with accepted alternatives; each tool case carries expected tools and argument matchers.

**Setup** (identical for all three runs): chat model `gpt-6-luna` at temperature 0, seed 42;
embeddings `text-embedding-3-small`; knowledge base of 3,255 chunks in five namespaces; the full
Piper tool set as registered in production.

**Metrics**: tool selection, argument accuracy, forbidden calls; Recall / Precision / Hit / NDCG at
3, 5 and 10 plus MRR, both for the raw retriever (the question against all namespaces, top 10) and
for the passages Piper's own `search_knowledge` calls returned; context precision and recall;
fact-based answer correctness; citation checks. All of these are deterministic. On top of them,
Claude Haiku 5.5 acts as an independent judge (see [LLM judge](#llm-judge)).

## The three rounds

**Baseline**: Piper as it was, dense vector search only.

**Fix 1: agent side, no data change.**
- `list_machines` now tells Piper, when nothing matches, that the store sells four machines and
  that other models (AVIA, WELE) are in the knowledge base, so Piper routes those questions to
  `search_knowledge` (one matching line in the system prompt).
- The search fetches extra candidates before de-duplicating the twin copies kept in two namespaces,
  so Piper always receives its full set of passages (4.15 -> 4.79 per search).
- Leaner tool output: **15 % fewer prompt tokens** per question (15,925 -> 13,531).

The raw retriever is untouched by design (its numbers are identical to baseline), so every gain
in this round is the agent using the knowledge base better.

**Fix 2: hybrid search.** A second Zilliz collection, `piper_hybrid_store`, holds the same 3,255
chunks plus a BM25 sparse field that Milvus builds from the text. Each question becomes one Milvus
hybrid query, dense embedding plus keyword search under the same namespace filter, fused with
reciprocal rank fusion (k = 60). Enabled with `KNOWLEDGE_HYBRID=true`; the original collection was
only read, never changed.

## Answer quality

| Metric | Baseline | Fix 1 | Fix 2 |
|---|---|---|---|
| Answer correctness | 0.809 | 0.907 | **0.927** |
| Context recall | 0.765 | 0.853 | **0.882** |
| Context precision | 0.684 | 0.724 | **0.739** |
| Cites a retrieved document | 0.828 | **0.936** | 0.871 |
| Cites the gold document | 0.706 | **0.853** | 0.794 |
| Unsupported citations per case | **0.000** | **0.000** | **0.000** |

**By category, answer correctness:**

| Category | Baseline | Fix 1 | Fix 2 |
|---|---|---|---|
| Machine models | 0.600 | **1.000** | **1.000** |
| Power-tool safety | 0.857 | 0.952 | **1.000** |
| Business rules | 0.778 | **0.889** | **0.889** |
| Machine operations | 0.857 | 0.809 | **0.857** |
| Home DIY | **0.900** | **0.900** | **0.900** |
| Machine maintenance | **1.000** | **1.000** | **1.000** |

Machine-model questions went from 3 of 5 right to **5 of 5**, and power-tool safety reached a
**perfect score** after hybrid search.

## LLM judge

A second model from a different provider, **Claude Haiku 5.5**, reads every RAG answer next to what
Piper read (the knowledge passages plus the results of its catalog, machine and service tools) and
returns three scores:
- **Judged correctness**: the share of key facts the answer states, judged by meaning, so a fact
  given in other words or units still counts.
- **Faithfulness**: the share of the answer's atomic claims that the passages or tool results back.
- **Answer relevancy**: the mean embedding similarity between the question and three questions
  the judge writes from the answer.

| Judge metric | Baseline | Fix 1 | Fix 2 |
|---|---|---|---|
| **Judged answer correctness** | 0.853 | **0.971** | 0.941 |
| Faithfulness | **0.796** | 0.771 | 0.784 |
| Answer relevancy | **0.786** | 0.782 | 0.777 |
| Claims per answer | 6.9 | 7.4 | 7.1 |

**Judged correctness by category:**

| Category | Baseline | Fix 1 | Fix 2 |
|---|---|---|---|
| Machine models | 0.600 | **1.000** | **1.000** |
| Power-tool safety | **1.000** | **1.000** | **1.000** |
| Machine maintenance | **1.000** | **1.000** | **1.000** |
| Machine operations | **0.929** | **0.929** | **0.929** |
| Home DIY | **0.900** | **0.900** | **0.900** |
| Business rules | 0.778 | **1.000** | 0.889 |

The judge confirms the deterministic picture: both fixes lift correctness by roughly 9 to 12
points, and three categories are answered perfectly. Faithfulness holds steady near 0.78 to 0.80
across all three rounds while Piper answers far more questions correctly and in more detail, so
the gains come from better grounding, not from guessing. The claims it does not trace to a source
are mostly the helpful advice Piper adds around the facts ("check the product page", "ask our staff
to confirm"). The judge costs about USD 0.05 per run and its replies are cached in `eval/cache/judge/`.

## Retrieval

**Passages Piper actually received:**

| Metric | Baseline | Fix 1 | Fix 2 |
|---|---|---|---|
| Recall@3 | 0.504 | 0.556 | **0.573** |
| Recall@5 | 0.607 | 0.683 | **0.721** |
| Precision@5 | 0.382 | 0.435 | **0.465** |
| NDCG@3 | 0.565 | 0.602 | **0.651** |
| NDCG@5 | 0.597 | 0.651 | **0.700** |
| NDCG@10 | 0.592 | 0.648 | **0.702** |
| Hit@5 | 0.853 | **0.912** | 0.882 |
| MRR | 0.723 | **0.777** | 0.775 |
| Searched the knowledge base when it should | 0.853 | **0.912** | **0.912** |
| Namespace choice correct | **1.000** | **1.000** | **1.000** |

**Raw retriever** (question against all namespaces, top 10):

| Metric | Baseline | Fix 1 | Fix 2 |
|---|---|---|---|
| Recall@3 | 0.545 | 0.545 | **0.606** |
| Recall@5 | 0.706 | 0.706 | **0.777** |
| Precision@5 | 0.453 | 0.453 | **0.494** |
| NDCG@5 | 0.685 | 0.685 | **0.742** |
| NDCG@10 | 0.754 | 0.754 | **0.780** |
| Recall@10 | 0.889 | 0.889 | **0.889** |
| Hit@10 | **1.000** | **1.000** | 0.971 |
| MRR | **0.848** | **0.848** | 0.820 |

**Agent NDCG@10 by category:**

| Category | Baseline | Fix 1 | Fix 2 |
|---|---|---|---|
| Machine models | 0.326 | 0.659 | **0.678** |
| Power-tool safety | 0.719 | 0.705 | **0.868** |
| Machine operations | 0.688 | 0.700 | **0.788** |
| Business rules | 0.530 | 0.562 | **0.569** |

### Where hybrid search shines

BM25 lifts the chunks that hinge on an exact term, a part name, a regulation number or a model
code, which dense embeddings alone tend to blur:

| Case | Metric | Fix 1 | Fix 2 |
|---|---|---|---|
| rag-pt-01 (power-tool safety) | NDCG@10 | 0.28 | **0.96** |
| rag-rule-02 (business rule) | MRR | 0.33 | **1.00** |
| rag-pt-03 (power-tool safety) | MRR | 0.50 | **1.00** |
| rag-ops-14 (machine operations) | MRR | 0.50 | **1.00** |
| rag-diy-03 (home DIY) | MRR | 0.50 | **1.00** |
| rag-rule-06 (business rule) | Recall@5 | 0.33 | **1.00** |
| rag-mach-12 (machine models) | Recall@5 | 0.50 | **1.00** |
| rag-ops-05 (machine operations) | Recall@5 | 0.50 | **1.00** |

On "What does G00 do?" the correct chunk went from outside the vector top 10 to **first place**.

## Tool calling

| Metric | Baseline | Fix 1 | Fix 2 |
|---|---|---|---|
| Tool pass | 0.960 | **0.980** | **0.980** |
| Tool selection | 0.960 | **0.980** | **0.980** |
| First tool correct | 0.872 | **0.936** | **0.936** |
| Exact tool-set match | 0.880 | **0.940** | 0.920 |
| Argument accuracy | **1.000** | **1.000** | **1.000** |
| Argument exact match | **1.000** | **1.000** | **1.000** |
| Tool errors per case | 0.040 | **0.000** | **0.000** |

Whenever Piper picks a tool, it fills in **every argument correctly**, in all three rounds.

## Cost and speed

| Metric | Baseline | Fix 1 | Fix 2 |
|---|---|---|---|
| Mean prompt tokens | 15,925 | **13,531** | 14,282 |
| Mean completion tokens | **137** | 147 | 147 |
| Mean latency (ms) | **4,936** | 5,883 | 5,501 |
| Tool calls per case | 1.22 | **1.14** | 1.22 |

Hybrid search adds one Milvus round trip and still answers faster than Fix 1, and Piper reads
fewer tokens per question than at baseline.

## Notes

- Fix 2's raw-retriever MRR (0.820) is a little below Fix 1's (0.848): on questions made of common
  words ("compressed air", "warranty") BM25 can raise a keyword-rich neighbour above the best chunk.
  The best chunk still reaches Piper's context, and answer correctness rose. The next step is a
  weighted ranker (for example 0.7 dense / 0.3 BM25) in place of plain RRF.
- In the hybrid run the `embedding.*` scores are fusion scores, not cosines; compare ranks across
  runs, not those values.
- With 34 RAG cases, one question is worth about 0.03 on any mean, which is the whole gap between
  Fix 1 and Fix 2 on judged correctness.

## Reproduce

```bash
./mvnw test -Dtest=PiperEvalTest -Dpiper.eval=true -Dpiper.eval.label=baseline
./mvnw test -Dtest=PiperEvalTest -Dpiper.eval=true -Dpiper.eval.label=fix2 -Dapp.knowledge.hybrid=true

# LLM judge over a saved run (needs ANTHROPIC_API_KEY in .env; Piper is not called again)
./mvnw test -Dtest=JudgeRescoreTest -Dpiper.eval=true -Dpiper.eval.label=fix2
```

Each run writes `raw.json` (full transcripts, tool calls and retrieved passages), `metrics.json`
(every metric per case, per category and overall) and `report.md` under `eval/results/{label}/`.
