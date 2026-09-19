package com.securesoc.service;

import com.securesoc.dto.EndpointStatusEvent;
import com.securesoc.entity.EndpointDevice;
import org.springframework.stereotype.Service;

/**
 * The only class in the codebase that knows the WebSocket destination name
 * for endpoint-status events - see EndpointEventPublisher's Javadoc for
 * why that's a deliberate boundary. Builds the DTO here (rather than have
 * callers build it) so the wire shape has exactly one source of truth.
 * Delivery scoping (who is authorized to receive a given endpoint's
 * status) is delegated to {@link ScopedWebSocketDelivery} - see that
 * class for the Admin/Faculty eligibility rule.
 */
@Service
public class WebSocketEndpointEventPublisher implements EndpointEventPublisher {

    private static final String ENDPOINT_STATUS_DESTINATION = "/queue/endpoints/status";

    private final ScopedWebSocketDelivery scopedDelivery;

    public WebSocketEndpointEventPublisher(ScopedWebSocketDelivery scopedDelivery) {
        this.scopedDelivery = scopedDelivery;
    }

    @Override
    public void publishStatusChange(EndpointDevice device) {
        EndpointStatusEvent event = new EndpointStatusEvent(
            device.getId(),
            device.getHostname(),
            device.getStatus().name(),
            device.getLastHeartbeatAt(),
            device.getLab() != null ? device.getLab().getName() : null
        );
        scopedDelivery.deliverToAuthorizedUsers(ENDPOINT_STATUS_DESTINATION, event, device.getId());
    }
}
