package com.ironoak.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * One retrievable passage of the policy document or the "For Dummies" books.
 *
 * This table is owned by the application, not by a Spring AI VectorStore: there is
 * deliberately no PgVectorStore bean, so the id stays BIGSERIAL and source/chapter/
 * section remain real columns instead of being buried in a metadata blob. Similarity
 * search is the hand-written cosine query in DocumentChunkRepository.
 */
@Entity
@Table(name = "document_chunk")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 'iron_oak_policies' | 'home_maintenance_dummies' | 'home_wiring_dummies' */
    @Column(nullable = false, length = 100)
    private String source;

    @Column(length = 300)
    private String chapter;

    @Column(length = 300)
    private String section;

    /** Chunk text WITH the chapter/section header prepended, as embedded. */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /**
     * Length must stay in step with the embedding model: text-embedding-3-large is
     * requested at 1024 dimensions in application.yml, matching VECTOR(1024) in
     * V1__init_schema.sql. Changing one without the others breaks inserts.
     */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1024)
    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    protected DocumentChunk() {
    }

    public DocumentChunk(String source, String chapter, String section,
                         String content, float[] embedding, Map<String, Object> metadata) {
        this.source = source;
        this.chapter = chapter;
        this.section = section;
        this.content = content;
        this.embedding = embedding;
        this.metadata = metadata;
    }

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getChapter() {
        return chapter;
    }

    public String getSection() {
        return section;
    }

    public String getContent() {
        return content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
