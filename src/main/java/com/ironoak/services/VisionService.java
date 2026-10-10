package com.ironoak.services;

import ai.onnxruntime.OrtException;
import com.ironoak.config.VisionProperties;
import com.ironoak.domain.ToolCategory;
import com.ironoak.dto.response.ClassificationResponse;
import com.ironoak.dto.response.ClassificationResponse.Match;
import com.ironoak.dto.response.ProductResponse;
import com.ironoak.exceptions.InvalidImageException;
import com.ironoak.mapper.ProductMapper;
import com.ironoak.repository.ProductRepository;
import com.ironoak.repository.ToolCategoryRepository;
import com.ironoak.vision.ConfidenceGate;
import com.ironoak.vision.ConfidenceGate.Decision;
import com.ironoak.vision.OnnxVisionClassifier;
import com.ironoak.vision.OnnxVisionClassifier.Prediction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Photo in, tool class and matching catalog products out. */
@Service
@Transactional(readOnly = true)
public class VisionService {

    // The top prediction plus four alternatives for a retake / "did you mean"
    // prompt.
    private static final int TOP_K = 5;

    private final OnnxVisionClassifier classifier;
    private final ConfidenceGate gate;
    private final VisionProperties properties;
    private final ToolCategoryRepository toolCategories;
    private final ProductRepository products;
    private final ProductMapper productMapper;

    public VisionService(OnnxVisionClassifier classifier,
            ConfidenceGate gate,
            VisionProperties properties,
            ToolCategoryRepository toolCategories,
            ProductRepository products,
            ProductMapper productMapper) {
        this.classifier = classifier;
        this.gate = gate;
        this.properties = properties;
        this.toolCategories = toolCategories;
        this.products = products;
        this.productMapper = productMapper;
    }

    public ClassificationResponse classify(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new InvalidImageException("No image provided");
        }
        List<Prediction> ranked;
        try {
            ranked = classifier.classify(imageBytes, TOP_K);

        } catch (IOException e) {
            throw new InvalidImageException("The file is not a readable image");

        } catch (OrtException e) {
            throw new IllegalStateException("Vision model failed to run", e);
        }

        Decision decision = gate.evaluate(ranked);

        Map<String, ToolCategory> categories = toolCategories
                .findByModelLabelIn(ranked.stream().map(Prediction::modelLabel).toList()).stream()
                .collect(Collectors.toMap(ToolCategory::getModelLabel, Function.identity()));

        Match top = toMatch(decision.top(), categories);

        List<Match> alternatives = decision.alternatives().stream()
                .map(p -> toMatch(p, categories)).toList();

        List<ProductResponse> matches = decision.accepted()
                ? productMapper.toResponses(products.findByVisionNameAndIsActiveTrue(top.modelLabel()))
                : List.of();

        return new ClassificationResponse(decision.accepted(),
                properties.getConfidenceThreshold(),
                top, alternatives, matches);
    }

    private Match toMatch(Prediction prediction,
            Map<String, ToolCategory> categories) {

        ToolCategory category = categories.get(prediction.modelLabel());
        // Every class has a tool_category row (VisionPipelineTest); fall back to the
        // label if not.
        String displayName = category == null ? prediction.modelLabel() : category.getDisplayName();

        return new Match(prediction.modelLabel(), displayName, prediction.score());
    }
}
