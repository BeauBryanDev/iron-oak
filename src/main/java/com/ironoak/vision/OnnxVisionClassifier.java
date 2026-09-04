package com.ironoak.vision;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.ironoak.config.VisionProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Runs the MobileNetV3 tool classifier.
 *
 * The exported graph outputs raw logits (shape [batch, 87]), NOT probabilities, so
 * softmax is applied here before anything compares a score against a threshold.
 * Skipping it makes the confidence gate meaningless - logits are unbounded and
 * routinely exceed 1.0 even for a wrong prediction.
 */
@Component
public class OnnxVisionClassifier {

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final ImagePreprocessor preprocessor;
    private final VisionProperties properties;
    private final List<String> labels;

    public OnnxVisionClassifier(OrtEnvironment environment,
                                OrtSession session,
                                ImagePreprocessor preprocessor,
                                VisionProperties properties,
                                List<String> toolClassLabels) {
        this.environment = environment;
        this.session = session;
        this.preprocessor = preprocessor;
        this.properties = properties;
        this.labels = toolClassLabels;
    }

    /** One scored class. score is a probability in [0,1] after softmax. */
    public record Prediction(String modelLabel, float score) {
    }

    /** Returns the topK predictions, highest probability first. */
    public List<Prediction> classify(byte[] imageBytes, int topK) throws IOException, OrtException {
        FloatBuffer input = preprocessor.toTensor(imageBytes);
        int size = properties.getInputSize();
        long[] shape = {1, 3, size, size};

        String inputName = session.getInputNames().iterator().next();
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, input, shape);
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {

            float[] logits = ((float[][]) result.get(0).getValue())[0];
            float[] probabilities = softmax(logits);

            List<Prediction> ranked = new ArrayList<>(probabilities.length);
            for (int i = 0; i < probabilities.length; i++) {
                ranked.add(new Prediction(labels.get(i), probabilities[i]));
            }
            ranked.sort(Comparator.comparing(Prediction::score).reversed());
            return ranked.subList(0, Math.min(topK, ranked.size()));
        }
    }

    /**
     * Numerically stable softmax: subtracting the max before exponentiating prevents
     * overflow to Infinity on large logits, which would yield NaN probabilities.
     */
    static float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float logit : logits) {
            max = Math.max(max, logit);
        }

        float sum = 0f;
        float[] exponentials = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exponentials[i] = (float) Math.exp(logits[i] - max);
            sum += exponentials[i];
        }

        for (int i = 0; i < exponentials.length; i++) {
            exponentials[i] /= sum;
        }
        return exponentials;
    }
}
