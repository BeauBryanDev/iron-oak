package com.ironoak.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class VisionControllerTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private List<String> toolClassLabels;

    /** Noise, not a real tool - this asserts the plumbing, not model accuracy. */
    private byte[] noisePng() throws Exception {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void classifiesAnUploadedImageWithoutAToken() throws Exception {
        String body = mockMvc.perform(multipart("/api/vision/classify")
                        .file(new MockMultipartFile("image", "photo.png", "image/png", noisePng())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidenceThreshold").value(0.55))
                .andExpect(jsonPath("$.alternatives.length()").value(4))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        JsonNode top = json.get("top");
        assertThat(toolClassLabels).contains(top.get("modelLabel").asText());
        assertThat(top.get("displayName").asText()).isNotBlank();
        assertThat(top.get("score").asDouble()).isBetween(0.0, 1.0);
        // alternatives are ranked below the top prediction
        assertThat(json.get("alternatives").get(0).get("score").asDouble())
                .isLessThanOrEqualTo(top.get("score").asDouble());

        boolean accepted = json.get("accepted").asBoolean();
        assertThat(accepted).isEqualTo(top.get("score").asDouble() >= 0.55);
        // products only come back for an accepted prediction, and they belong to the top class
        if (accepted) {
            assertThat(json.get("products")).isNotEmpty();
            json.get("products").forEach(p ->
                    assertThat(p.get("visionName").asText()).isEqualTo(top.get("modelLabel").asText()));
        } else {
            assertThat(json.get("products")).isEmpty();
        }
    }

    @Test
    void rejectsBadUploads() throws Exception {
        mockMvc.perform(multipart("/api/vision/classify")
                        .file(new MockMultipartFile("image", "notes.txt", "text/plain", "not an image".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is not a readable image"));

        mockMvc.perform(multipart("/api/vision/classify")
                        .file(new MockMultipartFile("image", "empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/vision/classify"))
                .andExpect(status().isBadRequest());
    }
}
