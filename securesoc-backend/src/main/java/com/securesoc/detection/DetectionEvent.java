package com.securesoc.detection;

/**
 * Marker interface for the concrete event data a {@link DetectionContext}
 * carries. Deliberately empty - it exists only to constrain what can be
 * passed as a context's event payload to purpose-built detection event
 * types, instead of an arbitrary {@code Object}.
 *
 * This keeps {@link DetectionContext} independent of any specific
 * telemetry entity (AuthFailureEvent, UsbEvent, VpnEvent, RunningApp,
 * IdleEvent, ...): those entities are not touched here, and no concrete
 * {@code DetectionEvent} implementation is introduced by this foundation.
 * When the first concrete detector is built (e.g. the future
 * RepeatedFailedLoginDetector), it will be paired with a small, purpose-
 * built {@code DetectionEvent} implementation (e.g. an
 * "AuthFailureDetectionEvent" record wrapping the fields that detector
 * needs) rather than every detector sharing one large event shape.
 *
 * A detector that needs its event data still narrows this interface with
 * an {@code instanceof} pattern match - but only to the small set of
 * purpose-built {@code DetectionEvent} types that actually exist, not to
 * anything an {@code Object} could have held.
 */
public interface DetectionEvent {
}
