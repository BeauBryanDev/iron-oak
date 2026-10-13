package com.ironoak.controller;

import com.ironoak.dto.response.ImageUploadResponse;
import com.ironoak.services.TemporaryImageService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Public temporary image intake for Piper chat vision requests. */
@RestController
@RequestMapping("/api/image")
public class ImageController {

    private final TemporaryImageService images;

    public ImageController(TemporaryImageService images) {
        this.images = images;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse upload(@RequestPart("image") MultipartFile image) throws IOException {

        return new ImageUploadResponse(images.store(image.getBytes()));
    }
}
