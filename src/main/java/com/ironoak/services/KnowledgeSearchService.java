package com.ironoak.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.ironoak.domain.enums.KnowledgeNamespace;
import com.ironoak.dto.response.KnowledgeHitResponse;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.milvus.MilvusFilterExpressionConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semantic search over the Milvus knowledge base (collection
 * piper_vector_store):
 * one embedding call and one Milvus search for all requested namespaces.
 *
 * It talks to Milvus with the client Spring AI creates, instead of going
 * through
 * Spring AI's MilvusVectorStore, because that store refuses any hit whose
 * metadata holds a null ("metadata cannot have null values"), and many chunks
 * have "page": null. Reading the hits here skips null values and leaves the
 * stored vectors untouched.
 */
@Service
public class KnowledgeSearchService {

    /**
     * The metadata key, not "metadata[\"namespace\"]": the Milvus filter converter
     * wraps every key as metadata["key"] itself.
     */
    static final String NAMESPACE_KEY = "namespace"; // iit gives me su much problems last weekend
    // dificult ot reach Milvus Vector-Store Collection from my Spring backend

    /**
     * Two books are stored in two namespaces (VMC650 manual, AVIA machines
     * catalog), so a search over both can return every passage twice. Fetching
     * twice topK plus a few leaves room to drop those twins and still return
     * topK passages (with a flat headroom of 4, 10 asked often gave 7 or 8).
     */
    static final int DUPLICATE_HEADROOM = 4;
    // Then field names written by scripts/upload_vectors.py (Spring AI's defaults)
    private static final String CONTENT_FIELD = "content";
    private static final String METADATA_FIELD = "metadata";
    private static final String VECTOR_FIELD = "embedding";
    /** BM25 sparse field of piper_hybrid_store, filled by Milvus from content */
    private static final String SPARSE_FIELD = "sparse";

    private final ObjectProvider<MilvusServiceClient> milvus;
    private final EmbeddingModel embeddings;
    private final String databaseName;
    private final String collectionName;
    private final HybridSettings hybrid;
    private volatile MilvusClientV2 hybridClient;

    /**
     * Hybrid search (app.knowledge.*): dense vectors plus BM25 over the same
     * chunks in piper_hybrid_store (built by scripts/build_hybrid_collection.py),
     * fused by Milvus with reciprocal rank fusion. Off = vectors only.
     */
    public record HybridSettings(boolean enabled, String collection, int rrfK, String uri, String token) {
    }

    public KnowledgeSearchService(ObjectProvider<MilvusServiceClient> milvus,
            EmbeddingModel embeddings,
            @Value("${spring.ai.vectorstore.milvus.database-name:default}") String databaseName,
            @Value("${spring.ai.vectorstore.milvus.collection-name}") String collectionName,
            @Value("${app.knowledge.hybrid:false}") boolean hybridEnabled,
            @Value("${app.knowledge.hybrid-collection:piper_hybrid_store}") String hybridCollection,
            @Value("${app.knowledge.rrf-k:60}") int rrfK,
            @Value("${spring.ai.vectorstore.milvus.client.host:localhost}") String host,
            @Value("${spring.ai.vectorstore.milvus.client.port:19530}") int port,
            @Value("${spring.ai.vectorstore.milvus.client.secure:false}") boolean secure,
            @Value("${spring.ai.vectorstore.milvus.client.token:}") String token) {

        this.milvus = milvus;
        this.embeddings = embeddings;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
        this.hybrid = new HybridSettings(hybridEnabled,
                hybridCollection,
                rrfK,
                (secure ? "https://" : "http://") + host + ":" + port,
                token);
    }

    public boolean hybridEnabled() {
        return hybrid.enabled();
    }

    public List<KnowledgeHitResponse> search(String query,
            List<KnowledgeNamespace> namespaces,
            int topK) {

        return searchDocuments(query, namespaces, topK).stream()
                .map(KnowledgeHitResponse::from)
                .toList();
    }

    /**
     * The same search, as Spring AI documents that keep the chunk id, score and
     * full metadata (source, chunk_index); the agent eval ranks these.
     */
    public List<Document> searchDocuments(String query,
            List<KnowledgeNamespace> namespaces,
            int topK) {

        if (hybrid.enabled()) {
            return withoutDuplicates(hybridSearch(query,
                    namespaces,
                    topK * 2 + DUPLICATE_HEADROOM), topK);
        }
        MilvusServiceClient client = milvus.getIfAvailable();

        if (client == null) {
            throw new IllegalStateException("The knowledge base (Milvus) is not configured");
        }
        SearchParam param = SearchParam.newBuilder()
                .withDatabaseName(databaseName)
                .withCollectionName(collectionName)
                .withMetricType(MetricType.COSINE)
                .withVectorFieldName(VECTOR_FIELD)
                .withFloatVectors(List.of(toList(embeddings.embed(query))))
                .withTopK(topK * 2 + DUPLICATE_HEADROOM)
                .withExpr(namespaceFilter(namespaces))
                .withOutFields(List.of(CONTENT_FIELD, METADATA_FIELD))
                .withParams("{}")
                .build();

        R<SearchResults> response = client.search(param);

        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new IllegalStateException("Knowledge search failed: " + response.getMessage(),
                    response.getException());
        }
        List<Document> hits = new ArrayList<>();
        SearchResultsWrapper results = new SearchResultsWrapper(response.getData().getResults());

        for (SearchResultsWrapper.IDScore row : results.getIDScore(0)) {

            hits.add(Document.builder()
                    .id(row.getStrID())
                    .text(String.valueOf(row.get(CONTENT_FIELD)))
                    .metadata(withoutNulls(row.get(METADATA_FIELD)))
                    .score((double) row.getScore())
                    .build());
        }
        return withoutDuplicates(hits, topK);
    }

    /**
     * One Milvus hybrid query: the question's embedding against the dense field
     * and its raw text against the BM25 field, both with the namespace filter,
     * fused with RRF. The score is the RRF score, not a cosine.
     */
    private List<Document> hybridSearch(String query,
            List<KnowledgeNamespace> namespaces,
            int candidates) {

        String filter = namespaceFilter(namespaces);
        AnnSearchReq dense = AnnSearchReq.builder()
                .vectorFieldName(VECTOR_FIELD)
                .vectors(List.of(new FloatVec(embeddings.embed(query))))
                .metricType(IndexParam.MetricType.COSINE)
                .expr(filter)
                .topK(candidates)
                .build();
        AnnSearchReq lexical = AnnSearchReq.builder()
                .vectorFieldName(SPARSE_FIELD)
                .vectors(List.of(new EmbeddedText(query)))
                .metricType(IndexParam.MetricType.BM25)
                .expr(filter)
                .topK(candidates)
                .build();
        SearchResp response = hybridClient().hybridSearch(HybridSearchReq.builder()
                .databaseName(databaseName)
                .collectionName(hybrid.collection())
                .searchRequests(List.of(dense, lexical))
                .ranker(new RRFRanker(hybrid.rrfK()))
                .topK(candidates)
                .outFields(List.of(CONTENT_FIELD, METADATA_FIELD))
                .build());

        List<Document> hits = new ArrayList<>();
        for (SearchResp.SearchResult row : response.getSearchResults().get(0)) {
            hits.add(Document.builder()
                    .id(String.valueOf(row.getId()))
                    .text(String.valueOf(row.getEntity().get(CONTENT_FIELD)))
                    .metadata(withoutNulls(row.getEntity().get(METADATA_FIELD)))
                    .score((double) row.getScore())
                    .build());
        }
        return hits;
    }

    /**
     * The v2 client, needed because only it can send raw text to a BM25 field;
     * created on first hybrid search with the same Zilliz settings.
     */
    private MilvusClientV2 hybridClient() {

        MilvusClientV2 client = hybridClient;
        if (client == null) {
            synchronized (this) {
                client = hybridClient;
                if (client == null) {
                    client = new MilvusClientV2(ConnectConfig.builder()
                            .uri(hybrid.uri())
                            .token(hybrid.token())
                            .dbName(databaseName)
                            .build());
                    hybridClient = client;
                }
            }
        }
        return client;
    }

    @PreDestroy
    void closeHybridClient() {
        if (hybridClient != null) {
            hybridClient.close();
        }
    }

    /**
     * metadata["namespace"] in [...], built by Spring AI's own Milvus converter.
     */
    static String namespaceFilter(List<KnowledgeNamespace> namespaces) {

        Object[] values = namespaces.stream()
                .map(KnowledgeNamespace::value)
                .toArray(Object[]::new);

        return new MilvusFilterExpressionConverter().convertExpression(
                new FilterExpressionBuilder().in(NAMESPACE_KEY, values).build());
    }

    /**
     * Keeps the first (best scored) copy of each passage, identified by source
     * file and chunk index, up to topK.
     */
    static List<Document> withoutDuplicates(List<Document> hits, int topK) {

        Set<String> seen = new HashSet<>();
        List<Document> unique = new ArrayList<>(topK);

        for (Document hit : hits) {

            Map<String, Object> metadata = hit.getMetadata();
            String key = metadata.get("source") + "#" + metadata.get("chunk_index");

            if (seen.add(key)) {
                unique.add(hit);
            }
            if (unique.size() == topK) {
                break;
            }
        }
        return unique;
    }

    /** The JSON metadata as a map, dropping nulls (e.g. "page": null). */
    private static Map<String, Object> withoutNulls(Object metadata) {

        Map<String, Object> values = new HashMap<>();

        if (metadata instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (v != null) {
                    values.put(String.valueOf(k), v);
                }
            });
            return values;
        }
        if (!(metadata instanceof JsonObject json)) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {

            JsonElement value = entry.getValue();

            if (value == null || value.isJsonNull()) {
                continue;
            }
            if (value.isJsonPrimitive()) {

                JsonPrimitive primitive = value.getAsJsonPrimitive();
                values.put(entry.getKey(), primitive.isNumber()
                        ? primitive.getAsNumber()
                        : primitive.getAsString());
            } else {
                values.put(entry.getKey(), value.toString());
            }
        }
        return values;
    }

    private static List<Float> toList(float[] vector) {

        List<Float> values = new ArrayList<>(vector.length);

        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
