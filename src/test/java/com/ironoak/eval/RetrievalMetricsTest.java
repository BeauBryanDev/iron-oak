package com.ironoak.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Hand-checked values for the ranking math and the text normalisation. */
class RetrievalMetricsTest {

    // ranks 1..4: miss, answer (2), miss, support (1)
    private static final List<Integer> GRADES = List.of(0, 2, 0, 1);

    @Test
    void rankingMetrics() {

        assertThat(RetrievalMetrics.reciprocalRank(GRADES)).isEqualTo(0.5);
        assertThat(RetrievalMetrics.hitAtK(GRADES, 1)).isZero();
        assertThat(RetrievalMetrics.hitAtK(GRADES, 3)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.precisionAtK(GRADES, 4)).isEqualTo(0.5);
        // (1/2 + 2/4) / 2
        assertThat(RetrievalMetrics.averagePrecision(GRADES)).isEqualTo(0.5);
        // DCG = 3/log2(3) + 1/log2(5); IDCG = 3/1 + 1/log2(3)
        double expected = (3 / log2(3) + 1 / log2(5)) / (3 + 1 / log2(3));
        assertThat(RetrievalMetrics.ndcgAtK(GRADES, List.of(2, 1), 4)).isCloseTo(expected, within(1e-12));
        assertThat(RetrievalMetrics.recallAtK(List.of("x", "a", "y", "b"),
                Map.of("a", 2, "b", 1, "c", 1), 3)).isCloseTo(1.0 / 3, within(1e-12));
    }

    @Test
    void normalisationMatchesThePythonValidator() {

        assertThat(TextNormalizer.normalize("Spindle speed 24 000 rpm, 1,500 kg")).isEqualTo("spindle speed 24000 rpm, 1500 kg");
        assertThat(TextNormalizer.normalize("± 0,004 mm, Ø 450")).isEqualTo("± 0,004 mm, ø 450");
        assertThat(TextNormalizer.normalize("Table 800 x 540  Bogotá ¼”")).isEqualTo("table 800x540 bogota 1/4”");
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
