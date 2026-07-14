package com.securesoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "securesoc.cors")
public record CorsProperties(
    String allowedOrigins
) {}
