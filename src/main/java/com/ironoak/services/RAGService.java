package com.ironoak.services;

import com.ironoak.config.RagProperties;
import com.ironoak.domain.DocumentChunk;
import com.ironoak.repository.DocumentChunkRepository;
import com.ironoak.repository.DocumentChunkRepository.ScoredChunk;
import com.ironoak.repository.VectorLiterals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retrieval over the policy document and the "For Dummies" books.
 *
 * Embeds the question with the same model that embedded the corpus, runs the hand-written
 * cosine query in DocumentChunkRepository, drops weak matches, and assembles a bounded
 * context block for Piper's prompt. There is no Spring AI VectorStore in the middle -
 * this class is the whole retrieval path.
 */
@Service
public class RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGService.class);

    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunks;
    private final RagProperties properties;

    public RAGService(EmbeddingModel embeddingModel,
                      DocumentChunkRepository chunks,
                      RagProperties properties) {
        this.embeddingModel = embeddingModel;
        this.chunks = chunks;
        this.properties = properties;
    }

    /** A retrieved passage and how closely it matched. similarity is in [-1, 1]. */
    public record Passage(Long id, String source, String chapter, String section,
                          String content, double similarity) {

        /** "Home Maintenance Dummies > Plumbing > Leaks" - used for citations. */
        public String citation() {
            StringBuilder text = new StringBuilder(source);
            if (StringUtils.hasText(chapter)) {
                text.append(" > ").append(chapter);
            }
            if (StringUtils.hasText(section)) {
                text.append(" > ").append(section);
            }
            return text.toString();
        }
    }

    /** Top matches across every corpus, weak ones removed. */
    @Transactional(readOnly = true)
    public List<Passage> retrieve(String question) {
        if (!StringUtils.hasText(question)) {
            return List.of();
        }

        float[] embedding = embeddingModel.embed(question);
        assertDimensions(embedding);

        List<ScoredChunk> scored =
                chunks.findNearestWithScore(VectorLiterals.of(embedding), properties.getTopK());

        List<Passage> passages = scored.stream()
                .filter(chunk -> chunk.getSimilarity() >= properties.getMinSimilarity())
                .map(chunk -> new Passage(chunk.getId(), chunk.getSource(), chunk.getChapter(),
                        chunk.getSection(), chunk.getContent(), chunk.getSimilarity()))
                .toList();

        log.debug("RAG retrieved {}/{} chunks above similarity {} for: {}",
                passages.size(), scored.size(), properties.getMinSimilarity(), question);
        return passages;
    }

    /**
     * Assembles retrieved passages into a prompt block, newest-strongest first, truncated
     * at maxContextChars on a passage boundary so no citation is left dangling.
     *
     * Returns an empty string when nothing clears the similarity floor - the caller should
     * treat that as "no grounding available" and let Piper say it does not know, rather
     * than answering from the base model.
     */
    public String buildContext(List<Passage> passages) {
        if (passages.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (Passage passage : passages) {
            String block = "[" + passage.citation() + "]\n" + passage.content() + "\n\n";
            if (context.length() + block.length() > properties.getMaxContextChars()) {
                log.debug("RAG context truncated at {} chars", context.length());
                break;
            }
            context.append(block);
        }
        return context.toString().stripTrailing();
    }

    /** Convenience: retrieve and format in one call. */
    @Transactional(readOnly = true)
    public String retrieveContext(String question) {
        return buildContext(retrieve(question));
    }

    /**
     * Embeds and stores one already-chunked passage. Chunking the source PDFs is a
     * separate ingestion concern; this is the write half of the retrieval contract.
     *
     * content must already have its chapter/section header prepended, matching the
     * schema comment - the embedded text and the stored text have to be identical or
     * retrieval scores drift from what the corpus was built with.
     */
    @Transactional
    public DocumentChunk index(String source, String chapter, String section,
                               String content, Map<String, Object> metadata) {
        float[] embedding = embeddingModel.embed(content);
        assertDimensions(embedding);
        return chunks.save(new DocumentChunk(source, chapter, section, content, embedding,
                metadata == null ? Map.of() : metadata));
    }

    /** Batch variant - one embedding call for the whole list rather than one per chunk. */
    @Transactional
    public List<DocumentChunk> indexAll(List<DocumentChunk> unembedded) {
        if (unembedded.isEmpty()) {
            return List.of();
        }

        List<float[]> embeddings = embeddingModel.embed(
                unembedded.stream().map(DocumentChunk::getContent).toList());

        List<DocumentChunk> embedded = new ArrayList<>(unembedded.size());
        for (int i = 0; i < unembedded.size(); i++) {
            DocumentChunk chunk = unembedded.get(i);
            float[] embedding = embeddings.get(i);
            assertDimensions(embedding);
            embedded.add(new DocumentChunk(chunk.getSource(), chunk.getChapter(),
                    chunk.getSection(), chunk.getContent(), embedding, chunk.getMetadata()));
        }
        return chunks.saveAll(embedded);
    }

    /**
     * Fails loudly on a dimension mismatch. Without this an inserted vector of the wrong
     * width is rejected by Postgres with an opaque error, and a wrongly-sized query vector
     * would silently return nonsense rankings.
     */
    private void assertDimensions(float[] embedding) {
        if (embedding.length != properties.getDimensions()) {
            throw new IllegalStateException(
                    "Embedding model returned %d dimensions but document_chunk.embedding is vector(%d). Check spring.ai.openai.embedding.options.dimensions."
                            .formatted(embedding.length, properties.getDimensions()));
        }
    }
}
