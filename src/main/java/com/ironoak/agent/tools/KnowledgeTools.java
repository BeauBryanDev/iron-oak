package com.ironoak.agent.tools;

import com.ironoak.domain.enums.KnowledgeNamespace;
import com.ironoak.dto.response.KnowledgeHitResponse;
import com.ironoak.exceptions.BusinessRuleException;
import com.ironoak.services.KnowledgeSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Read-only RAG search over Iron Oak policies, manuals and safety guides. */
@Component
public class KnowledgeTools {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 8;

    private final KnowledgeSearchService knowledge;

    public KnowledgeTools(KnowledgeSearchService knowledge) {
        this.knowledge = knowledge;
    }

    @Tool(name = "search_knowledge", description = "Search the Iron Oak knowledge base (company policies, manuals, safety guides) and return the most relevant passages with their source. Use it for warranty, shipping and company rules, how-to and maintenance questions, machine operation and tool safety. Never use it for prices, stock, order status or bookings; use the database tools for those. Answer only from the returned passages, name the source you used (doc_title, plus the page when present, e.g. 'according to the Iron Oak Business Rules'), and say so when they do not cover the question. If this tool returns an error, tell the customer the knowledge base is unavailable instead of answering from general knowledge.")
    public List<KnowledgeHitResponse> searchKnowledge(
            @ToolParam(description = "The question or topic to look up, in English, as a short self-contained sentence.") String query,
            @ToolParam(required = false, description = "Where to search, one or more of: iron_oak (company policies, warranty, shipping, product and service descriptions), home_do_it_yourself (home repair and maintenance), machine_operations (operating milling machines, CNC and EMC2), machine_maintenance (industrial maintenance and lubrication), power_tools (hand and power tool safety and regulations). Omit to search all.") List<String> namespaces,
            @ToolParam(required = false, description = "Number of passages, default 5, maximum 8.") Integer top_k) {

        if (query == null || query.isBlank()) {
            throw new BusinessRuleException("query is required");
        }

        int effectiveTopK = top_k == null ? DEFAULT_TOP_K : top_k;
        if (effectiveTopK < 1 || effectiveTopK > MAX_TOP_K) {
            throw new BusinessRuleException("top_k must be between 1 and 8");
        }

        List<KnowledgeNamespace> selected = namespaces == null || namespaces.isEmpty()
                ? List.of(KnowledgeNamespace.values())
                : namespaces.stream().map(KnowledgeNamespace::fromValue).distinct().toList();

        return knowledge.search(query.strip(),
                selected, effectiveTopK);
    }
}