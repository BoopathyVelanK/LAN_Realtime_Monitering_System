package com.securesoc.dto.agent;

/** agent.py logs data.get("status") from this response (see send_heartbeat). */
public record AgentHeartbeatResponse(
    String status
) {}
