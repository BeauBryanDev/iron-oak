package com.ironoak.domain;

import com.ironoak.repository.ToolCategoryRepository;
import com.ironoak.vision.ConfidenceGate;
import com.ironoak.vision.OnnxVisionClassifier;
import com.ironoak.vision.OnnxVisionClassifier.Prediction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Testcontainers
class VisionPipelineTest {

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
    private OnnxVisionClassifier classifier;

    @Autowired
    private ConfidenceGate gate;

    @Autowired
    private ToolCategoryRepository toolCategories;

    @Autowired
    private List<String> toolClassLabels;

    @Test
    void seedsAllClassesAndEveryLabelResolves() {
        assertThat(toolCategories.count()).isEqualTo(87);
        assertThat(toolClassLabels).hasSize(87);

        // The join key must hold for every class the model can emit.
        for (String label : toolClassLabels) {
            assertThat(toolCategories.findByModelLabel(label))
                    .as("no tool_category row for model label '%s'", label)
                    .isPresent();
        }

        assertThat(toolCategories.findByModelLabel("wrench_ai"))
                .get().extracting(ToolCategory::getDisplayName).isEqualTo("Wrench");
    }

    @Test
    void runsRealInferenceAndReturnsCalibratedProbabilities() throws Exception {
        List<Prediction> ranked = classifier.classify(syntheticImage(), 5);

        assertThat(ranked).hasSize(5);
        assertThat(ranked).isSortedAccordingTo(
                (a, b) -> Float.compare(b.score(), a.score()));

        // Every score is a real probability, and the label is a known class.
        for (Prediction p : ranked) {
            assertThat(p.score()).isBetween(0f, 1f);
            assertThat(toolClassLabels).contains(p.modelLabel());
        }

        // The full distribution sums to 1 - re-derive over all 87 to confirm.
        List<Prediction> all = classifier.classify(syntheticImage(), 87);
        float sum = 0f;
        for (Prediction p : all) {
            sum += p.score();
        }
        assertThat(sum).isCloseTo(1.0f, within(1e-4f));

        ConfidenceGate.Decision decision = gate.evaluate(ranked);
        assertThat(decision.top()).isEqualTo(ranked.get(0));
        assertThat(decision.accepted())
                .isEqualTo(ranked.get(0).score() >= 0.55f);
    }

    /** Noise, not a real tool - this asserts the plumbing, not model accuracy. */
    private byte[] syntheticImage() throws Exception {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(120, 118, 115));
        g.fillRect(0, 0, 640, 480);
        g.setColor(new Color(60, 60, 65));
        g.fillRect(200, 180, 240, 60);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
