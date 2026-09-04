package com.ironoak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the app.rag.* block in application.yml. */
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    /** How many chunks to pull from pgvector before filtering. */
    private int topK = 5;

    /**
     * Cosine similarity floor, applied in Java after the top-k. Filtering on distance
     * in SQL would defeat the HNSW index - see DocumentChunkRepository.
     */
    private double minSimilarity = 0.35;

    /** Hard cap on assembled context, so a long retrieval cannot crowd out the prompt. */
    private int maxContextChars = 6000;

    /** Must equal the vector(n) width in V1__init_schema.sql. */
    private int dimensions = 1024;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinSimilarity() {
        return minSimilarity;
    }

    public void setMinSimilarity(double minSimilarity) {
        this.minSimilarity = minSimilarity;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public void setMaxContextChars(int maxContextChars) {
        this.maxContextChars = maxContextChars;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }
}
