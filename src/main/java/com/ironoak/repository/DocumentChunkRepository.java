package com.ironoak.repository;

import com.ironoak.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Similarity search over document_chunk, written by hand rather than delegated to a
 * Spring AI VectorStore.
 *
 * The queries are native because <=> has no HQL equivalent. <=> is pgvector's cosine
 * DISTANCE operator: 0.0 means identical direction, 2.0 means opposite, so results are
 * ordered ASCENDING and similarity is reported as (1 - distance).
 *
 * The ORDER BY ... LIMIT shape is what lets PostgreSQL use the HNSW index
 * (idx_document_chunk_embedding, built with vector_cosine_ops). Wrapping the distance
 * in an expression, or filtering on it in WHERE, defeats the index and forces a scan.
 *
 * Embeddings are passed as a pgvector string literal - "[0.12,-0.03,...]" - and cast
 * in SQL. That sidesteps binding a float[] through a native query parameter, which is
 * where the JDBC type mapping gets fragile. Use VectorLiterals.of(float[]) to build it.
 */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /** Top-k nearest chunks across every source. */
    @Query(value = """
            SELECT *
            FROM document_chunk
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> findNearest(@Param("embedding") String embedding,
                                    @Param("limit") int limit);

    /** Top-k nearest chunks restricted to one corpus. */
    @Query(value = """
            SELECT *
            FROM document_chunk
            WHERE embedding IS NOT NULL
              AND source = :source
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<DocumentChunk> findNearestBySource(@Param("embedding") String embedding,
                                            @Param("source") String source,
                                            @Param("limit") int limit);

    /**
     * Top-k nearest chunks with their cosine similarity, for callers that need to drop
     * weak matches. The distance is computed once in the SELECT and reused by ORDER BY,
     * so the index still applies.
     */
    @Query(value = """
            SELECT id, source, chapter, section, content,
                   1 - (embedding <=> CAST(:embedding AS vector)) AS similarity
            FROM document_chunk
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<ScoredChunk> findNearestWithScore(@Param("embedding") String embedding,
                                           @Param("limit") int limit);

    /** Projection for the scored query. Cosine similarity in [-1, 1]; 1.0 is identical. */
    interface ScoredChunk {
        Long getId();

        String getSource();

        String getChapter();

        String getSection();

        String getContent();

        double getSimilarity();
    }
}
