package com.securesoc.dto.agent;

public record AgentHeartbeatRequest(
    Double cpuUsagePct,
    Double ramUsagePct,
    Double diskUsagePct,
    String ipAddress
) {}
