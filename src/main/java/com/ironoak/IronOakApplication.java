package com.ironoak;

import com.ironoak.config.CorsProperties;
import com.ironoak.config.JwtProperties;
import com.ironoak.config.VisionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({VisionProperties.class,
        JwtProperties.class, CorsProperties.class})
public class IronOakApplication {

    public static void main(String[] args) {
        SpringApplication.run(IronOakApplication.class, args);
    }
}
