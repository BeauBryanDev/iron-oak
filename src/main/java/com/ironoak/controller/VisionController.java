package com.ironoak.controller;

import com.ironoak.dto.response.ClassificationResponse;
import com.ironoak.exceptions.InvalidImageException;
import com.ironoak.services.VisionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Public: customers send a photo of a tool and get the matching catalog
 * products back.
 */
@RestController
@RequestMapping("/api/vision")
public class VisionController {

    private final VisionService vision;

    public VisionController(VisionService vision) {
        this.vision = vision;
    }

    @PostMapping("/classify")
    public ClassificationResponse classify(@RequestPart("image") MultipartFile image) {
        try {
            return vision.classify(image.getBytes());

        } catch (IOException e) {
            throw new InvalidImageException("Could not read the uploaded file");
        }
    }
}
