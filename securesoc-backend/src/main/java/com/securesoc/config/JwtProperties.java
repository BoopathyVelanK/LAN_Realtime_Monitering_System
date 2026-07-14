package com.securesoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "securesoc.jwt")
public record JwtProperties(
    String secret,
    long accessTokenTtlSeconds,
    long refreshTokenTtlSeconds
) {}
