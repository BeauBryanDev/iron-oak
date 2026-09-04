package com.ironoak.vision;

import com.ironoak.config.VisionProperties;
import com.ironoak.vision.OnnxVisionClassifier.Prediction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Decides whether a prediction is trustworthy enough to act on. Below the threshold
 * Piper should ask the customer to confirm or retake the photo rather than silently
 * looking up the wrong tool.
 *
 * Operates on softmax probabilities - see OnnxVisionClassifier.
 */
@Component
public class ConfidenceGate {

    private final VisionProperties properties;

    public ConfidenceGate(VisionProperties properties) {
        this.properties = properties;
    }

    public record Decision(boolean accepted, Prediction top, List<Prediction> alternatives) {
    }

    public Decision evaluate(List<Prediction> ranked) {
        if (ranked.isEmpty()) {
            return new Decision(false, null, List.of());
        }
        Prediction top = ranked.get(0);
        boolean accepted = top.score() >= properties.getConfidenceThreshold();
        return new Decision(accepted, top, ranked.subList(1, ranked.size()));
    }
}
