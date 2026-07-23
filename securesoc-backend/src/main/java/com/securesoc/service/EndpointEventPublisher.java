package com.securesoc.service;

import com.securesoc.entity.EndpointDevice;

/**
 * Abstraction over "tell interested listeners an endpoint's status just
 * changed" - callers (AgentService, EndpointOfflineSweeper) depend on
 * this interface, not on SimpMessagingTemplate or any other transport
 * detail directly. WebSocketEndpointEventPublisher is the only
 * implementation today; this seam exists so that swapping or adding a
 * transport (e.g. an outbound message queue later) never touches the
 * service layer that decides *when* to publish, only the one class that
 * decides *how*.
 */
public interface EndpointEventPublisher {

    /**
     * Publishes the given device's current status. Callers are expected
     * to call this only when the status actually changed (comparing
     * before/after a save) - this method itself does not deduplicate.
     */
    void publishStatusChange(EndpointDevice device);
}
