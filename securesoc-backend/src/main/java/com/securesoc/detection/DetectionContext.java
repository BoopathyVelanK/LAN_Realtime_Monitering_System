package com.securesoc.detection;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal, telemetry-agnostic event information a {@link Detector} needs to
 * evaluate a {@code DetectionRule}. Deliberately generic so the detection
 * foundation supports every current and future event stream (auth failures,
 * USB, VPN, running processes, idle, ...) without hardcoding any one of
 * their entities into this package - see AuthFailureEvent, UsbEvent,
 * VpnEvent etc. in the entity package for the concrete telemetry rows.
 *
 * {@code eventSource} must match {@code DetectionRule.eventSource} (e.g.
 * "AUTH_FAILURE") so {@link DetectionEngine} can select the rules that
 * apply to a given context.
 *
 * {@code sourcePayload} is an optional escape hatch: it carries a reference
 * to the originating event/entity (or any data a concrete detector needs)
 * without this record needing to know its type. No detection logic reads
 * or interprets it here - that is entirely up to the detector that
 * declares support for the relevant rule.
 */
public record DetectionContext(
    String eventSource,
    UUID endpointId,
    UUID userId,
    Instant occurredAt,
    Object sourcePayload
) {
}
