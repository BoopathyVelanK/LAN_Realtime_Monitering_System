package com.securesoc.dto.monitoring;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Mirrors agent.py's run_monitoring_cycle() idle POST body:
 * {"idleSeconds": <int>} - only ever sent when
 * collector.get_idle_seconds() returns non-null. */
public record IdleEventRequest(
    @NotNull @PositiveOrZero Integer idleSeconds
) {}
