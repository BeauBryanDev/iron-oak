package com.ironoak.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled housekeeping (order reservation expiry, expired refresh-token cleanup).
 * On unless app.scheduling.enabled=false, which the test run sets (pom.xml surefire config):
 * tests call the jobs directly instead.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
