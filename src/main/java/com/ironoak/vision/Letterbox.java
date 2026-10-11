package com.ironoak.vision;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Ultralytics-style letterbox for the YOLO end-mill detector, in plain Java (no
 * OpenCV): scale the photo to fit a square of {@code size} keeping its aspect
 * ratio, centre it on gray 114 padding, then RGB, 0-1, NCHW.
 *
 * The padding offsets are rounded the way Ultralytics' LetterBox does
 * ({@code round(d - 0.1)} on the top/left side), so boxes map back to the
 * original photo with {@link #toOriginalX} / {@link #toOriginalY}.
 */
public final class Letterbox {

    static final int PAD_GRAY = 114;

    private final float[] tensor;
    private final int size;
    private final double scale;
    private final int padLeft;
    private final int padTop;
    private final int originalWidth;
    private final int originalHeight;

    private Letterbox(float[] tensor,
            int size,
            double scale,
            int padLeft,
            int padTop,
            int originalWidth,
            int originalHeight) {

        this.tensor = tensor;
        this.size = size;
        this.scale = scale;
        this.padLeft = padLeft;
        this.padTop = padTop;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
    }

    /** Decodes the bytes (JPEG, PNG, GIF, BMP) and letterboxes them. */
    public static Letterbox of(byte[] imageBytes, int size) throws IOException {

        BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (source == null) {

            throw new IOException("Unsupported or corrupt image data");
        }
        return of(source, size);
    }

    public static Letterbox of(BufferedImage source, int size) {

        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min((double) size / width, (double) size / height);
        int newWidth = (int) Math.round(width * scale);
        int newHeight = (int) Math.round(height * scale);
        int padLeft = (int) Math.round((size - newWidth) / 2.0 - 0.1);
        int padTop = (int) Math.round((size - newHeight) / 2.0 - 0.1);

        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(new Color(PAD_GRAY, PAD_GRAY, PAD_GRAY));
            graphics.fillRect(0, 0, size, size);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, padLeft,
                    padTop, newWidth,
                    newHeight, null);
        } finally {
            graphics.dispose();
        }

        // NCHW: all red values, then all green, then all blue, scaled to 0-1
        int plane = size * size;
        float[] chw = new float[3 * plane];
        int[] pixels = canvas.getRGB(0, 0, size,
                size, null, 0, size);

        for (int i = 0; i < plane; i++) {

            int rgb = pixels[i];
            chw[i] = (rgb >> 16 & 0xFF) / 255f;
            chw[plane + i] = (rgb >> 8 & 0xFF) / 255f;
            chw[2 * plane + i] = (rgb & 0xFF) / 255f;
        }
        return new Letterbox(chw, size, scale,
                padLeft, padTop,
                width, height);
    }

    public float[] tensor() {
        return tensor;
    }

    public int size() {
        return size;
    }

    /** Model-space x (0..size) back to the original photo, clamped to its edges. */
    public float toOriginalX(float x) {
        return clamp((float) ((x - padLeft) / scale), originalWidth);
    }

    public float toOriginalY(float y) {
        return clamp((float) ((y - padTop) / scale), originalHeight);
    }

    public int originalWidth() {
        return originalWidth;
    }

    public int originalHeight() {
        return originalHeight;
    }

    private static float clamp(float value, int max) {

        return Math.max(0f, Math.min(value, max));
    }
}
