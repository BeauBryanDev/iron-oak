package com.ironoak.domain;

import com.ironoak.repository.DocumentChunkRepository;
import com.ironoak.repository.DocumentChunkRepository.ScoredChunk;
import com.ironoak.repository.VectorLiterals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
class DocumentChunkSearchTest {

    private static final int DIMENSIONS = 1024;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DocumentChunkRepository chunks;

    /** A unit vector pointing along one axis, so cosine distances are predictable. */
    private static float[] axis(int index) {
        float[] v = new float[DIMENSIONS];
        v[index] = 1.0f;
        return v;
    }

    /** Halfway between two axes - cosine similarity 0.707 to each. */
    private static float[] between(int a, int b) {
        float[] v = new float[DIMENSIONS];
        v[a] = 0.7071068f;
        v[b] = 0.7071068f;
        return v;
    }

    @BeforeEach
    void seed() {
        chunks.deleteAll();
        chunks.saveAll(List.of(
                new DocumentChunk("iron_oak_policies", "Returns", "Window",
                        "Unused tools may be returned within 30 days with a receipt.",
                        axis(0), Map.of("page", 3)),
                new DocumentChunk("home_maintenance_dummies", "Plumbing", "Leaks",
                        "Shut off the supply valve before removing a faucet cartridge.",
                        between(0, 1), Map.of("page", 118)),
                new DocumentChunk("home_wiring_dummies", "Circuits", "Breakers",
                        "A tripped breaker should be reset only after the fault is found.",
                        axis(1), Map.of("page", 44))));
    }

    @Test
    void ordersByCosineDistanceNearestFirst() {
        List<DocumentChunk> results = chunks.findNearest(VectorLiterals.of(axis(0)), 3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getChapter()).isEqualTo("Returns");        // distance 0.0
        assertThat(results.get(1).getChapter()).isEqualTo("Plumbing");       // distance ~0.29
        assertThat(results.get(2).getChapter()).isEqualTo("Circuits");       // distance 1.0
    }

    @Test
    void reportsCosineSimilarityNotDistance() {
        List<ScoredChunk> scored = chunks.findNearestWithScore(VectorLiterals.of(axis(0)), 3);

        // 1 - distance: identical vector scores 1.0, orthogonal scores 0.0.
        assertThat(scored.get(0).getSimilarity()).isCloseTo(1.0, within(1e-6));
        assertThat(scored.get(1).getSimilarity()).isCloseTo(0.7071068, within(1e-5));
        assertThat(scored.get(2).getSimilarity()).isCloseTo(0.0, within(1e-6));

        assertThat(scored.get(0).getContent()).contains("30 days");
        assertThat(scored.get(0).getSource()).isEqualTo("iron_oak_policies");
    }

    @Test
    void filtersByCorpus() {
        List<DocumentChunk> results =
                chunks.findNearestBySource(VectorLiterals.of(axis(0)), "home_wiring_dummies", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSource()).isEqualTo("home_wiring_dummies");
    }

    @Test
    void roundTripsTheVectorAndJsonbColumns() {
        DocumentChunk stored = chunks.findNearest(VectorLiterals.of(axis(1)), 1).get(0);

        assertThat(stored.getEmbedding()).hasSize(DIMENSIONS);
        assertThat(stored.getEmbedding()[1]).isEqualTo(1.0f);
        assertThat(stored.getMetadata()).containsEntry("page", 44);
    }

    @Test
    void rejectsMalformedEmbeddings() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> VectorLiterals.of(new float[]{Float.NaN})).getMessage())
                .contains("non-finite");
    }
}
