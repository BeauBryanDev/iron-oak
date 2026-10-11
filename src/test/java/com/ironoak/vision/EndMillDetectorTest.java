package com.ironoak.vision;

import ai.onnxruntime.OrtEnvironment;
import com.ironoak.vision.EndMillDetector.Box;
import com.ironoak.vision.EndMillDetector.Detection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Plain unit test (no Spring). The raw-output values were computed with Python
 * onnxruntime on the same ONNX file and the same synthetic 512x256 image,
 * letterboxed by hand (no resize, 128 rows of gray 114 above and below).
 */
class EndMillDetectorTest {

    private static final List<String> LABELS = List.of(
            "ball_nose_end_mill_1mm", "ball_nose_end_mill_2mm", "ball_nose_end_mill_3mm",
            "fishtail_end_mill_1mm", "fishtail_end_mill_2mm", "fishtail_end_mill_3mm");

    private static EndMillDetector detector;

    @BeforeAll
    static void load() throws Exception {
        detector = new EndMillDetector(OrtEnvironment.getEnvironment());
    }

    @Test
    void readsClassNamesAndInputSizeFromTheModel() {
        assertThat(detector.classNames()).isEqualTo(LABELS);
        assertThat(detector.inputSize()).isEqualTo(512);
    }

    @Test
    void javaInferenceMatchesPythonOnnxRuntime() {
        BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 512; x++) {
                image.setRGB(x, y, (x % 256) << 16 | (y % 256) << 8 | (x + y) % 256);
            }
        }
        Letterbox letterbox = Letterbox.of(image, 512);
        assertThat(letterbox.tensor()[0]).isCloseTo(114 / 255f, within(1e-6f)); // top padding
        assertThat(letterbox.tensor()[128 * 512 + 300]).isCloseTo(44 / 255f, within(1e-6f)); // red of x=300

        float[][] output = detector.run(letterbox);
        assertThat(output.length).isEqualTo(10);
        assertThat(output[0].length).isEqualTo(5376);

        float[] candidate0 = { 4.7240987f, 4.3427153f, 9.2379599f, 8.8055210f };
        float[] candidate4752 = { 259.79245f, 320.08340f, 18.600784f, 129.74184f };
        for (int i = 0; i < 4; i++) {
            assertThat(output[i][0]).isCloseTo(candidate0[i], within(1e-2f));
            assertThat(output[i][4752]).isCloseTo(candidate4752[i], within(1e-2f));
        }
        // no end mill in a gradient: nothing clears the threshold
        assertThat(EndMillDetector.decode(output, LABELS, letterbox,
                EndMillDetector.CONFIDENCE_THRESHOLD, EndMillDetector.IOU_THRESHOLD)).isEmpty();
    }

    @Test
    void mergesOverlappingBoxesAcrossClassesAndMapsThemBackToThePhoto() {
        // 1024x512 photo -> scale 0.5, 128 px of padding on top
        Letterbox letterbox = Letterbox.of(new BufferedImage(1024, 512, BufferedImage.TYPE_INT_RGB), 512);

        float[][] output = new float[10][4];
        setCandidate(output, 0, 100, 200, 40, 80, 3, 0.90f); // fishtail 1mm
        setCandidate(output, 1, 102, 201, 40, 80, 0, 0.60f); // ball nose 1mm on the same tool
        setCandidate(output, 2, 400, 300, 50, 50, 5, 0.50f); // a second tool
        setCandidate(output, 3, 250, 250, 30, 30, 2, 0.20f); // under the threshold

        List<Detection> detections = EndMillDetector.decode(output, LABELS, letterbox,
                EndMillDetector.CONFIDENCE_THRESHOLD, EndMillDetector.IOU_THRESHOLD);

        assertThat(detections).extracting(Detection::label)
                .containsExactly("fishtail_end_mill_1mm", "fishtail_end_mill_3mm");
        assertThat(detections.get(0).confidence()).isEqualTo(0.90f);
        assertThat(detections.get(0).box()).isEqualTo(new Box(160f, 64f, 240f, 224f));
    }

    private static void setCandidate(float[][] output, int c, float cx, float cy, float w, float h,
            int classIndex, float score) {
        output[0][c] = cx;
        output[1][c] = cy;
        output[2][c] = w;
        output[3][c] = h;
        output[4 + classIndex][c] = score;
    }
}
