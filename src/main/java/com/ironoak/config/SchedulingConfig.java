package com.ironoak.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled housekeeping (expired refresh-token cleanup). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
