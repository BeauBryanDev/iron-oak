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

    @Tool(name = "search_knowledge", description = "Search the Iron Oak knowledge base: company policies, warranty and shipping rules, the machine catalogs (AVIA and WELE models), machine manuals, how-to and safety guides. Not for prices, stock, orders or bookings (use the database tools). Answer only from the returned passages, name the doc_title you used (and page when present), and say when they do not cover the question. If the tool fails, say the knowledge base is unavailable.")
    public List<KnowledgeHitResponse> searchKnowledge(
            @ToolParam(description = "The question as a short self-contained English sentence.") String query,
            @ToolParam(required = false, description = "One or more of: iron_oak (policies, shipping, products, machine catalogs), home_do_it_yourself (home repair), machine_operations (milling, CNC, EMC2, metal cutting), machine_maintenance (industrial maintenance), power_tools (tool safety). Omit to search all.") List<String> namespaces,
            @ToolParam(required = false, description = "Passages, default 5, maximum 8.") Integer top_k) {

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