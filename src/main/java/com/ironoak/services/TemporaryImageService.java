package com.ironoak.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ironoak.exceptions.InvalidImageException;
import com.ironoak.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Holds uploaded chat images in memory only; nothing is written to disk or the
 * database.
 */
@Service
public class TemporaryImageService {

    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final Cache<String, byte[]> images = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(1_000)
            .build();

    public String store(byte[] imageBytes) {

        validate(imageBytes);
        String imageId = UUID.randomUUID().toString();
        images.put(imageId, imageBytes.clone());

        return imageId;
    }

    public byte[] get(String imageId) {

        if (imageId == null || imageId.isBlank()) {
            throw new ResourceNotFoundException("Image", imageId);
        }
        byte[] image = images.getIfPresent(imageId);
        if (image == null) {
            throw new ResourceNotFoundException("Image", imageId);
        }
        return image.clone();
    }

    private static void validate(byte[] imageBytes) {

        if (imageBytes == null || imageBytes.length == 0) {
            throw new InvalidImageException("No image provided");
        }
        if (imageBytes.length > MAX_BYTES) {
            throw new InvalidImageException("Image must not exceed 8 MB");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new InvalidImageException("The file is not a readable image");
            }
        } catch (IOException e) {
            throw new InvalidImageException("The file is not a readable image");
        }
    }
}
