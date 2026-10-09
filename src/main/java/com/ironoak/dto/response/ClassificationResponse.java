package com.ironoak.dto.response;

import java.util.List;

/**
 * accepted is false when the top score is below confidenceThreshold: the caller should
 * ask for a retake or let the customer pick from the alternatives, and products is empty.
 * score is a softmax probability in [0,1].
 */
public record ClassificationResponse(
        boolean accepted,
        float confidenceThreshold,
        Match top,
        List<Match> alternatives,
        List<ProductResponse> products) {

    public record Match(String modelLabel, String displayName, float score) {
    }
}
