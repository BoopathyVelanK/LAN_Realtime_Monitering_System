package com.securesoc.dto.agent;

import java.util.UUID;

/** agent.py reads endpointId, agentToken, status, message from this
 * response verbatim (see SecureSocAgent.register_with_retry). agentToken
 * is the ONLY time the raw agent token is ever transmitted - the backend
 * stores only its SHA-256 hash from this point on. */
public record AgentRegisterResponse(
    UUID endpointId,
    String agentToken,
    String status,
    String message
) {}
