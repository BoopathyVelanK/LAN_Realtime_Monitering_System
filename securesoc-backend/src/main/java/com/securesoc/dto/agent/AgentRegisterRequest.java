package com.securesoc.dto.agent;

import jakarta.validation.constraints.NotBlank;

/** Field names match collector.py's collect_registration_payload() keys
 * exactly (agent.py sends this as JSON to POST /agents/register). */
public record AgentRegisterRequest(
    @NotBlank String hostname,
    @NotBlank String macAddress,
    String ipAddress,
    String osName,
    String osVersion,
    String cpuInfo,
    Integer ramMb,
    Integer diskGb,
    String agentVersion,
    String labId // optional - unassigned until an admin assigns a lab
) {}
