package com.ironoak.vision;

import com.ironoak.dto.response.EndMillResponse;
import com.ironoak.exceptions.InvalidImageException;
import com.ironoak.exceptions.ResourceNotFoundException;
import com.ironoak.services.EndMillService;
import com.ironoak.vision.EndMillDetector.Box;
import com.ironoak.vision.EndMillDetector.Detection;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Photo in, the end mill it shows and its catalog entry out. No HTTP endpoint:
 * Piper calls {@link #identify} in-process as an agent tool (agent/tools).
 *
 * The most confident box after NMS is the answer; its class name is the
 * end_mills.vision_label, looked up through EndMillService.byVisionLabel
 * (active rows only).
 */
@Service
public class EndMillVisionService {

    private final EndMillDetector detector;
    private final EndMillService endMills;

    public EndMillVisionService(EndMillDetector detector,
            EndMillService endMills) {

        this.detector = detector;
        this.endMills = endMills;
    }

    /**
     * What Piper gets back. {@code detected} is false when no box clears the
     * threshold; {@code endMill} is null when the detected class is not on sale
     * (inactive or missing); {@code toolsInImage} counts the separate tools found,
     * so Piper can say which one it is describing.
     */
    public record EndMillIdentification(
            boolean detected,
            String visionLabel,
            Float confidence,
            Box box,
            int toolsInImage,
            EndMillResponse endMill,
            String message) {
    }

    public EndMillIdentification identify(byte[] imageBytes) {

        if (imageBytes == null || imageBytes.length == 0) {
            throw new InvalidImageException("No image provided");
        }
        List<Detection> detections;
        try {
            detections = detector.detect(imageBytes);
        } catch (IOException e) {
            throw new InvalidImageException("The file is not a readable image");
        }

        if (detections.isEmpty()) {
            return new EndMillIdentification(false, null, null, null, 0, null,
                    "No end mill recognised in the photo");
        }
        Detection top = detections.get(0);
        try {
            EndMillResponse endMill = endMills.byVisionLabel(top.label());
            return new EndMillIdentification(true, top.label(), top.confidence(), top.box(),
                    detections.size(), endMill, "Recognised " + endMill.name());

        } catch (ResourceNotFoundException e) {
            return new EndMillIdentification(true, top.label(), top.confidence(), top.box(),
                    detections.size(), null, "Recognised " + top.label() + ", but it is not in the catalog right now");
        }
    }
}
