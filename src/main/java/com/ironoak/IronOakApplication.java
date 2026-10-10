package com.ironoak;

import com.ironoak.config.CheckoutProperties;
import com.ironoak.config.CorsProperties;
import com.ironoak.config.JwtProperties;
import com.ironoak.config.SecurityProperties;
import com.ironoak.config.ShippingProperties;
import com.ironoak.config.StripeProperties;
import com.ironoak.config.VisionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({VisionProperties.class,
        JwtProperties.class, CorsProperties.class, SecurityProperties.class,
        CheckoutProperties.class, StripeProperties.class,
        ShippingProperties.class})
public class IronOakApplication {

    public static void main(String[] args) {
        SpringApplication.run(IronOakApplication.class, args);
    }
}
