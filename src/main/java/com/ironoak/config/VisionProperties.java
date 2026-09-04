package com.ironoak.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds the app.vision.* block in application.yml. */
@ConfigurationProperties(prefix = "app.vision")
public class VisionProperties {

    /** Classpath or filesystem location of the exported MobileNetV3 model. */
    private String modelPath = "classpath:models/hardware_store_mobilenetv3.onnx";

    /** Minimum softmax probability required to accept a prediction. */
    private float confidenceThreshold = 0.55f;

    /** Square input edge the model was exported with. */
    private int inputSize = 224;

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(float confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }

    public int getInputSize() {
        return inputSize;
    }

    public void setInputSize(int inputSize) {
        this.inputSize = inputSize;
    }
}
