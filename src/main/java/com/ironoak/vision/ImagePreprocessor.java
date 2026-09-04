package com.ironoak.vision;

import com.ironoak.config.VisionProperties;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;

/**
 * Turns an uploaded image into the NCHW float tensor the model expects.
 *
 * The normalization constants below are the torchvision ImageNet defaults, which is
 * what a standard MobileNetV3 fine-tune uses. If the training script used different
 * mean/std values, predictions will be quietly wrong rather than fail - so these must
 * match ml/ training preprocessing exactly.
 */
@Component
public class ImagePreprocessor {

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    private final VisionProperties properties;

    public ImagePreprocessor(VisionProperties properties) {
        this.properties = properties;
    }

    /** Decodes, center-crops to square, resizes, and normalizes into a CHW float buffer. */
    public FloatBuffer toTensor(byte[] imageBytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (source == null) {
            throw new IOException("Unsupported or corrupt image data");
        }

        int size = properties.getInputSize();
        BufferedImage resized = resize(centerCrop(source), size);

        // CHW layout: all red values, then all green, then all blue.
        float[] chw = new float[3 * size * size];
        int plane = size * size;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = resized.getRGB(x, y);
                int index = y * size + x;
                chw[index] = (((rgb >> 16 & 0xFF) / 255f) - MEAN[0]) / STD[0];
                chw[plane + index] = (((rgb >> 8 & 0xFF) / 255f) - MEAN[1]) / STD[1];
                chw[2 * plane + index] = (((rgb & 0xFF) / 255f) - MEAN[2]) / STD[2];
            }
        }
        return FloatBuffer.wrap(chw);
    }

    /** Crops the largest centered square, so resizing does not distort aspect ratio. */
    private BufferedImage centerCrop(BufferedImage source) {
        int edge = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - edge) / 2;
        int y = (source.getHeight() - edge) / 2;
        return source.getSubimage(x, y, edge, edge);
    }

    private BufferedImage resize(BufferedImage source, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        var graphics = target.createGraphics();
        try {
            graphics.drawImage(source.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }
}
