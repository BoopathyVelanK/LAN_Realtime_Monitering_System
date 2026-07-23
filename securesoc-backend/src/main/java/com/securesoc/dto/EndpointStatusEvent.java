package com.securesoc.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Pushed over /topic/endpoints/status whenever an endpoint's ONLINE/OFFLINE
 * status actually changes (a heartbeat bringing it back online, or
 * EndpointOfflineSweeper marking it stale after a missed heartbeat
 * timeout) - never sent on every heartbeat, never polled.
 *
 * Field names (endpointId, hostname, status, lastHeartbeatAt) mirror the
 * EndpointStatusEvent type already defined in frontend/src/types/api.ts
 * exactly - that frontend contract existed before this backend support
 * did (see frontend/src/ws/stompClient.ts), so this DTO conforms to it
 * rather than the other way around. labName is the one additive field on
 * top of that existing contract (nullable, backward compatible).
 */
public record EndpointStatusEvent(
    UUID endpointId,
    String hostname,
    String status,
    Instant lastHeartbeatAt,
    String labName
) {}
