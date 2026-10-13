package com.ironoak.agent;

import com.ironoak.services.TemporaryImageService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporaryImageServiceTest {

    @Test
    void storesReadableImageInMemoryAndReturnsACopy() throws Exception {
        TemporaryImageService images = new TemporaryImageService();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", output);

        byte[] original = output.toByteArray();
        String imageId = images.store(original);
        byte[] loaded = images.get(imageId);
        loaded[0] = (byte) (loaded[0] ^ 1);

        assertThat(images.get(imageId)).isEqualTo(original);
        assertThat(imageId).isNotBlank();
    }

    @Test
    void rejectsNonImageBytes() {
        TemporaryImageService images = new TemporaryImageService();

        assertThatThrownBy(() -> images.store("not an image".getBytes()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("The file is not a readable image");
    }
}
