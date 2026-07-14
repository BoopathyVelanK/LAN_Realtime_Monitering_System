package com.securesoc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "securesoc.agent")
public record AgentProperties(
    String registrationSecret,
    long heartbeatTimeoutSeconds
) {}
