package com.ironoak.vision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SoftmaxTest {

    @Test
    void producesProbabilitiesSummingToOne() {
        float[] probabilities = OnnxVisionClassifier.softmax(new float[]{2.1f, -0.4f, 8.7f, 0.0f});

        float sum = 0f;
        for (float p : probabilities) {
            assertThat(p).isBetween(0f, 1f);
            sum += p;
        }
        assertThat(sum).isCloseTo(1.0f, within(1e-5f));
        assertThat(probabilities[2]).isGreaterThan(probabilities[0]);
    }

    @Test
    void doesNotOverflowOnLargeLogits() {
        float[] probabilities = OnnxVisionClassifier.softmax(new float[]{1000f, 999f, -1000f});

        for (float p : probabilities) {
            assertThat(Float.isNaN(p)).isFalse();
            assertThat(Float.isInfinite(p)).isFalse();
        }
        assertThat(probabilities[0]).isGreaterThan(0.5f);
    }

    @Test
    void rawLogitsWouldDefeatTheConfidenceGate() {
        // A logit of 8.7 trivially clears a 0.55 threshold; its actual probability
        // is what the gate must see. This is the bug the softmax step prevents.
        float[] logits = {2.1f, -0.4f, 8.7f, 0.0f};
        assertThat(logits[0]).isGreaterThan(0.55f);
        assertThat(OnnxVisionClassifier.softmax(logits)[0]).isLessThan(0.55f);
    }
}
