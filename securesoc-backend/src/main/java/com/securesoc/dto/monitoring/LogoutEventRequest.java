package com.securesoc.dto.monitoring;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Mirrors agent.py's send_logout_event() payload exactly:
 * {"osUsername": ..., "sessionId": null, "logoutTime": ISO-8601 string}. */
public record LogoutEventRequest(
    @NotBlank String osUsername,
    String sessionId,
    Instant logoutTime
) {}
