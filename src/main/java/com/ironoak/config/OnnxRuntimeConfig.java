package com.ironoak.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

@Configuration
public class OnnxRuntimeConfig {

    /**
     * One environment per JVM. ORT sessions are thread-safe for inference, so a single
     * session bean is shared across requests rather than reloading the 17MB graph.
     */
    @Bean(destroyMethod = "close")
    public OrtEnvironment ortEnvironment() {
        return OrtEnvironment.getEnvironment();
    }

    @Bean(destroyMethod = "close")
    public OrtSession toolClassifierSession(OrtEnvironment env,
                                            VisionProperties properties,
                                            ResourceLoader resourceLoader
                                        ) throws IOException, OrtException {

        try (InputStream in = resourceLoader.getResource(properties.getModelPath()).getInputStream()) {
            return env.createSession(
                in.readAllBytes(), 
            new OrtSession.SessionOptions()
        );
        }
    }

    /**
     * Class labels ordered by model output index, read from the same idx_to_class.json
     * the training run produced. Index i in the logits vector is labels.get(i).
     */
    @Bean
    public List<String> toolClassLabels(ResourceLoader resourceLoader, 
        
        ObjectMapper objectMapper) throws IOException {

        try (InputStream in = resourceLoader.getResource("classpath:models/idx_to_class.json").
        getInputStream()) {

            Map<String, String> byIndex = objectMapper.readValue(in, Map.class);

            return byIndex.entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey())))
                    .map(Map.Entry::getValue)
                    .toList();
        }
    }
}
