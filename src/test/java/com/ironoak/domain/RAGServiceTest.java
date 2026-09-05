package com.ironoak.domain;

import com.ironoak.repository.DocumentChunkRepository;
import com.ironoak.services.RAGService;
import com.ironoak.services.RAGService.Passage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class RAGServiceTest {

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

    /** Stubbed so tests never call OpenAI and scores stay deterministic. */
    @MockitoBean
    private EmbeddingModel embeddingModel;

    @Autowired
    private RAGService ragService;

    @Autowired
    private DocumentChunkRepository chunks;

    private static float[] axis(int index) {
        float[] v = new float[DIMENSIONS];
        v[index] = 1.0f;
        return v;
    }

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
    void ranksByRelevanceAndDropsWeakMatches() {
        when(embeddingModel.embed(anyString())).thenReturn(axis(0));

        List<Passage> passages = ragService.retrieve("what is the return window?");

        // The orthogonal chunk scores 0.0 and falls below the 0.35 floor.
        assertThat(passages).hasSize(2);
        assertThat(passages.get(0).chapter()).isEqualTo("Returns");
        assertThat(passages.get(0).similarity()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(passages.get(1).chapter()).isEqualTo("Plumbing");
        assertThat(passages).noneMatch(p -> "Circuits".equals(p.chapter()));
    }

    @Test
    void buildsCitedContextBlock() {
        when(embeddingModel.embed(anyString())).thenReturn(axis(0));

        String context = ragService.retrieveContext("what is the return window?");

        assertThat(context)
                .contains("[iron_oak_policies > Returns > Window]")
                .contains("30 days")
                .contains("[home_maintenance_dummies > Plumbing > Leaks]");
        assertThat(context.indexOf("iron_oak_policies"))
                .isLessThan(context.indexOf("home_maintenance_dummies"));
    }

    @Test
    void returnsEmptyContextWhenNothingIsRelevant() {
        // Orthogonal to every stored chunk - everything scores 0.0.
        when(embeddingModel.embed(anyString())).thenReturn(axis(500));

        assertThat(ragService.retrieve("unrelated question")).isEmpty();
        assertThat(ragService.retrieveContext("unrelated question")).isEmpty();
    }

    @Test
    void ignoresBlankQuestionsWithoutCallingTheModel() {
        assertThat(ragService.retrieve("  ")).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(embeddingModel);
    }

    @Test
    void failsLoudlyOnDimensionMismatch() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[3072]);

        assertThatThrownBy(() -> ragService.retrieve("anything"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3072")
                .hasMessageContaining("vector(1024)");
    }

    @Test
    void indexesNewChunksWithEmbeddings() {
        when(embeddingModel.embed(anyString())).thenReturn(axis(7));

        var saved = ragService.index("iron_oak_policies", "Warranty", "Power Tools",
                "Power tools carry a 24 month manufacturer warranty.", Map.of("page", 9));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmbedding()).hasSize(DIMENSIONS);

        when(embeddingModel.embed(anyString())).thenReturn(axis(7));
        List<Passage> found = ragService.retrieve("how long is the warranty?");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).content()).contains("24 month");
    }
}
