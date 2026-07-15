package com.securesoc.dto.monitoring;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Mirrors agent.py's send_login_event() payload exactly:
 * {"osUsername": ..., "sessionId": null, "loginTime": ISO-8601 string}. */
public record LoginEventRequest(
    @NotBlank String osUsername,
    String sessionId,
    Instant loginTime
) {}
