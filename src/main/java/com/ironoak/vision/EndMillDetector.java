package com.ironoak.vision;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import jakarta.annotation.PreDestroy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The YOLOv11-nano end-mill detector (ml/End_Mills_Vision.ipynb, 6 classes
 * whose
 * names are the end_mills.vision_label values).
 *
 * Class names and input size are read from the Ultralytics metadata in the ONNX
 * file. The export has no NMS (end2end False): the raw output is
 * [1, 4 + classes, candidates], each candidate a box (cx, cy, w, h in model
 * pixels) plus one score per class. Candidates under CONFIDENCE_THRESHOLD are
 * dropped, then overlapping boxes are merged regardless of class, because the
 * model can put two neighbouring classes on the same tool and the shop needs
 * one
 * answer per tool. Has its own session, separate from the MobileNet
 * classifier's.
 */
@Component
public class EndMillDetector {

    static final String MODEL_PATH = "models/end_mills_model.onnx";

    public static final float CONFIDENCE_THRESHOLD = 0.27f;
    public static final float IOU_THRESHOLD = 0.45f;

    private static final Pattern NAME_ENTRY = Pattern.compile("(\\d+)\\s*:\\s*'([^']*)'");
    private static final Pattern FIRST_INT = Pattern.compile("(\\d+)");

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final List<String> classNames;
    private final int inputSize;

    /** A box in original-photo pixels (top-left x1/y1, bottom-right x2/y2). */
    public record Box(float x1, float y1, float x2, float y2) {

        float area() {
            return Math.max(0f, x2 - x1) * Math.max(0f, y2 - y1);
        }
    }

    /** One detected tool: its class name (= end_mills.vision_label) and score. */
    public record Detection(String label, float confidence, Box box) {
    }

    public EndMillDetector(OrtEnvironment environment) throws IOException, OrtException {

        this.environment = environment;
        try (InputStream in = new ClassPathResource(MODEL_PATH).getInputStream()) {
            this.session = environment.createSession(in.readAllBytes(),
                    new OrtSession.SessionOptions());
        }
        this.inputName = session.getInputNames().iterator().next();

        Map<String, String> metadata = session.getMetadata().getCustomMetadata();
        this.classNames = parseNames(metadata.get("names"));
        this.inputSize = parseInputSize(metadata.get("imgsz"));

        long[] outputShape = ((TensorInfo) session.getOutputInfo().values().iterator().next().getInfo())
                .getShape();

        if (outputShape.length != 3 || outputShape[1] != 4 + classNames.size()) {
            throw new IllegalStateException(MODEL_PATH + " output " + java.util.Arrays.toString(outputShape)
                    + " does not match its " + classNames.size() + " class names");
        }
    }

    /**
     * Every tool found in the photo after NMS, most confident first; empty when
     * nothing clears the threshold.
     */
    public List<Detection> detect(byte[] imageBytes) throws IOException {

        Letterbox letterbox = Letterbox.of(imageBytes, inputSize);
        return decode(run(letterbox),
                classNames,
                letterbox,
                CONFIDENCE_THRESHOLD,
                IOU_THRESHOLD);
    }

    /** Raw model output for one letterboxed photo: [4 + classes][candidates]. */
    float[][] run(Letterbox letterbox) {

        long[] shape = { 1, 3, inputSize, inputSize };
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment,
                FloatBuffer.wrap(letterbox.tensor()), shape);
                OrtSession.Result result = session.run(Map.of(inputName, tensor))) {

            return ((float[][][]) result.get(0).getValue())[0];

        } catch (OrtException e) {
            throw new IllegalStateException("End-mill detector failed to run", e);
        }
    }

    public List<String> classNames() {
        return classNames;
    }

    public int inputSize() {
        return inputSize;
    }

    @PreDestroy
    void close() throws OrtException {
        session.close();
    }

    /**
     * Raw output [4 + classes][candidates] -> thresholded, class-agnostic NMS,
     * boxes mapped back to the original photo.
     */
    static List<Detection> decode(float[][] output,
            List<String> names,
            Letterbox letterbox,
            float confidenceThreshold,
            float iouThreshold) {

        int candidates = output[0].length;
        List<Detection> above = new ArrayList<>();

        for (int c = 0; c < candidates; c++) {

            int bestClass = -1;
            float bestScore = confidenceThreshold;

            for (int k = 0; k < names.size(); k++) {

                float score = output[4 + k][c];

                if (score >= bestScore) {
                    bestScore = score;
                    bestClass = k;
                }
            }
            if (bestClass < 0) {
                continue;
            }
            float cx = output[0][c];
            float cy = output[1][c];
            float halfW = output[2][c] / 2f;
            float halfH = output[3][c] / 2f;
            Box box = new Box(letterbox.toOriginalX(cx - halfW),
                    letterbox.toOriginalY(cy - halfH),
                    letterbox.toOriginalX(cx + halfW),
                    letterbox.toOriginalY(cy + halfH));

            above.add(new Detection(names.get(bestClass), bestScore, box));
        }
        return nonMaxSuppression(above, iouThreshold);
    }

    /**
     * Greedy NMS ignoring class: keep the best box, drop any box overlapping a kept
     * one.
     */
    static List<Detection> nonMaxSuppression(List<Detection> detections, float iouThreshold) {

        List<Detection> sorted = new ArrayList<>(detections);
        sorted.sort(Comparator.comparing(Detection::confidence).reversed());
        List<Detection> kept = new ArrayList<>();

        for (Detection candidate : sorted) {

            boolean overlaps = false;

            for (Detection keep : kept) {

                if (iou(candidate.box(), keep.box()) > iouThreshold) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(candidate);
            }
        }
        return List.copyOf(kept);
    }

    static float iou(Box a, Box b) {

        float interW = Math.min(a.x2(), b.x2()) - Math.max(a.x1(), b.x1());
        float interH = Math.min(a.y2(), b.y2()) - Math.max(a.y1(), b.y1());
        if (interW <= 0 || interH <= 0) {
            return 0f;
        }
        float intersection = interW * interH;
        return intersection / (a.area() + b.area() - intersection);
    }

    /** Ultralytics writes names as a Python dict: {0: 'a', 1: 'b'}. */
    static List<String> parseNames(String raw) {

        if (raw == null) {
            throw new IllegalStateException(MODEL_PATH + " has no 'names' metadata");
        }
        TreeMap<Integer, String> byIndex = new TreeMap<>();
        Matcher matcher = NAME_ENTRY.matcher(raw);
        while (matcher.find()) {
            byIndex.put(Integer.parseInt(matcher.group(1)), matcher.group(2));
        }
        List<String> names = List.copyOf(byIndex.values());
        if (names.isEmpty() || !byIndex.containsKey(0) || byIndex.lastKey() != names.size() - 1) {
            throw new IllegalStateException(MODEL_PATH + " has unreadable class names: " + raw);
        }
        return names;
    }

    /** imgsz is "[512, 512]"; the model is square, so the first value is enough. */
    static int parseInputSize(String raw) {

        Matcher matcher = raw == null ? null : FIRST_INT.matcher(raw);
        if (matcher == null || !matcher.find()) {
            throw new IllegalStateException(MODEL_PATH + " has no 'imgsz' metadata");
        }
        return Integer.parseInt(matcher.group(1));
    }
}
