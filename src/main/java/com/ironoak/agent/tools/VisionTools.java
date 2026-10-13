package com.ironoak.agent.tools;

import com.ironoak.dto.response.ClassificationResponse;
import com.ironoak.services.TemporaryImageService;
import com.ironoak.services.VisionService;
import com.ironoak.vision.EndMillVisionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Piper's MobileNet-V3 hardware-tool identification adapter. */
@Component
public class VisionTools {

    private final TemporaryImageService images;
    private final VisionService vision;
    private final EndMillVisionService endMillVision;

    public VisionTools(TemporaryImageService images,
            VisionService vision,
            EndMillVisionService endMillVision) {

        this.images = images;
        this.vision = vision;
        this.endMillVision = endMillVision;
    }

    @Tool(name = "identify_tool_from_image", description = "Identify a hardware or power tool from a previously uploaded chat image. Call this only when the customer asks whether Iron Oak sells or carries the tool shown. The image_id is temporary and expires after 15 minutes. Returns the MobileNet-V3 prediction, confidence, alternatives, and matching active catalog products.")
    public ClassificationResponse identifyToolFromImage(
            @ToolParam(description = "Temporary image reference returned by POST /api/image.") String image_id) {

        return vision.classify(images.get(image_id));
    }

    @Tool(name = "identify_end_mill_from_image", description = "Identify end mills in a previously uploaded chat image using the YOLOv11 detector. Call this when the customer asks about an end mill shown in the image. The image_id is temporary and expires after 15 minutes. Returns the detected class, confidence, bounding box, number of tools found, and the active catalog match when available.")
    public EndMillVisionService.EndMillIdentification identifyEndMillFromImage(
            @ToolParam(description = "Temporary image reference returned by POST /api/image.") String image_id) {

        return endMillVision.identify(images.get(image_id));
    }
}
