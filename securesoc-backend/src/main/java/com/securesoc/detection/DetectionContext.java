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
 * {@code event} carries whatever event-specific data a detector needs,
 * typed as {@link DetectionEvent} rather than {@code Object}. This is
 * deliberately not a generic type parameter or a reflection-based lookup -
 * it is a plain marker interface that a future concrete
 * {@code DetectionEvent} implementation (paired with its own detector)
 * will implement, so a detector still has to narrow it with an
 * {@code instanceof} pattern match, but only against a small, known set of
 * purpose-built types rather than an arbitrary {@code Object} cast.
 * {@code event} may be {@code null} when a context carries no
 * detector-specific payload beyond the fields already on this record.
 */
public record DetectionContext(
    String eventSource,
    UUID endpointId,
    UUID userId,
    Instant occurredAt,
    DetectionEvent event
) {
}
