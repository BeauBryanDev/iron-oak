package com.ironoak.prognosis;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The CNC machine-health MLP (ml/CNC_Prognosis_Health.ipynb, AI4I 2020): 11
 * features in,
 * 5 sigmoid probabilities out (Machine failure, TWF, HDF, PWF, OSF).
 * 
 * Label order, feature order and the per-label thresholds come from the
 * metadata embedded in
 * the ONNX file; mean and scale come from the train-only StandardScaler export.
 * Startup fails
 * if the scaler and the model disagree on the feature order, rather than
 * predicting from
 * misaligned inputs. Has its own session, separate from the vision
 * classifier's.
 */
@Component
public class MachineHealthModel {

    public static final String MODEL_VERSION = "piper_machine_health_v1";
    static final String MODEL_PATH = "models/" + MODEL_VERSION + ".onnx";
    static final String SCALER_PATH = "models/piper_machine_health_scaler.json";
    static final String METADATA_KEY = "piper_machine_health_metadata";

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final List<String> featureOrder;
    private final List<String> labelOrder;
    private final Map<String, Double> thresholds;
    private final double[] mean;
    private final double[] scale;

    public MachineHealthModel(OrtEnvironment environment,
            ObjectMapper json) throws IOException, OrtException {

        this.environment = environment;
        try (InputStream in = new ClassPathResource(MODEL_PATH).getInputStream()) {

            this.session = environment.createSession(in.readAllBytes(), new OrtSession.SessionOptions());
        }
        this.inputName = session.getInputNames().iterator().next();

        String rawMetadata = session.getMetadata().getCustomMetadata().get(METADATA_KEY);

        if (rawMetadata == null) {

            throw new IllegalStateException(MODEL_PATH + " has no " + METADATA_KEY + " metadata");
        }
        JsonNode metadata = json.readTree(rawMetadata);
        this.featureOrder = strings(metadata.get("feature_order"));
        this.labelOrder = strings(metadata.get("label_order"));
        Map<String, Double> byLabel = new LinkedHashMap<>();

        for (String label : labelOrder) {

            byLabel.put(label, metadata.get("thresholds").get(label).asDouble());
        }
        this.thresholds = Map.copyOf(byLabel);

        JsonNode scaler;

        try (InputStream in = new ClassPathResource(SCALER_PATH).getInputStream()) {
            scaler = json.readTree(in);
        }
        if (!featureOrder.equals(strings(scaler.get("feature_order")))) {

            throw new IllegalStateException("Scaler feature order does not match the model's: "
                    + strings(scaler.get("feature_order")) + " vs " + featureOrder);
        }
        this.mean = doubles(scaler.get("mean"));
        this.scale = doubles(scaler.get("scale"));

        if (mean.length != featureOrder.size() || scale.length != featureOrder.size()) {

            throw new IllegalStateException("Scaler has " + mean.length + " means for "
                    + featureOrder.size() + " features");
        }
    }

    /**
     * Scales the raw features (keyed by the notebook's feature names) and returns
     * one
     * probability per label, in labelOrder().
     */
    public float[] predict(Map<String, Double> rawFeatures) {

        float[] scaled = new float[featureOrder.size()];

        for (int i = 0; i < scaled.length; i++) {

            Double value = rawFeatures.get(featureOrder.get(i));
            if (value == null) {
                throw new IllegalArgumentException("Missing feature " + featureOrder.get(i));
            }
            scaled[i] = (float) ((value - mean[i]) / scale[i]);
        }
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(scaled),
                new long[] { 1, scaled.length });

                OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            return ((float[][]) result.get(0).getValue())[0];

        } catch (OrtException e) {
            throw new IllegalStateException("Machine-health inference failed", e);
        }
    }

    public List<String> labelOrder() {
        return labelOrder;
    }

    public double threshold(String label) {
        return thresholds.get(label);
    }

    @PreDestroy
    void close() throws OrtException {
        session.close();
    }

    private static List<String> strings(JsonNode array) {

        List<String> values = new ArrayList<>();
        array.forEach(n -> values.add(n.asText()));

        return List.copyOf(values);
    }

    private static double[] doubles(JsonNode array) {

        double[] values = new double[array.size()];

        for (int i = 0; i < values.length; i++) {

            values[i] = array.get(i).asDouble();
        }
        return values;
    }
}
