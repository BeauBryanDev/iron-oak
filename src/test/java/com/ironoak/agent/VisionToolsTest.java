package com.ironoak.agent;

import com.ironoak.agent.tools.VisionTools;
import com.ironoak.dto.response.ClassificationResponse;
import com.ironoak.services.TemporaryImageService;
import com.ironoak.services.VisionService;
import com.ironoak.vision.EndMillVisionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionToolsTest {

    @Test
    void resolvesTemporaryImageAndDelegatesToMobileNetVisionService() {
        TemporaryImageService images = mock(TemporaryImageService.class);
        VisionService vision = mock(VisionService.class);
        EndMillVisionService endMills = mock(EndMillVisionService.class);
        VisionTools tools = new VisionTools(images, vision, endMills);
        byte[] image = {1, 2, 3};
        ClassificationResponse response = new ClassificationResponse(
                true, 0.55f,
                new ClassificationResponse.Match("wrench_ai", "Wrench", 0.94f),
                java.util.List.of(), java.util.List.of());
        when(images.get("image-123")).thenReturn(image);
        when(vision.classify(image)).thenReturn(response);

        assertThat(tools.identifyToolFromImage("image-123")).isSameAs(response);
        verify(images).get("image-123");
        verify(vision).classify(image);
    }
    @Test
    void resolvesTemporaryImageAndDelegatesToYoloEndMillService() {
        TemporaryImageService images = mock(TemporaryImageService.class);
        VisionService vision = mock(VisionService.class);
        EndMillVisionService endMills = mock(EndMillVisionService.class);
        VisionTools tools = new VisionTools(images, vision, endMills);
        byte[] image = {4, 5, 6};
        EndMillVisionService.EndMillIdentification response = new EndMillVisionService.EndMillIdentification(
                true, "ball_nose_end_mill_2mm", 0.91f, null, 1, null, "Recognised ball nose end mill");
        when(images.get("image-456")).thenReturn(image);
        when(endMills.identify(image)).thenReturn(response);

        assertThat(tools.identifyEndMillFromImage("image-456")).isSameAs(response);
        verify(images).get("image-456");
        verify(endMills).identify(image);
    }
}
