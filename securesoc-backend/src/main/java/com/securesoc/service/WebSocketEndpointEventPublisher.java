package com.securesoc.service;

import com.securesoc.dto.EndpointStatusEvent;
import com.securesoc.entity.EndpointDevice;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * The only class in the codebase that knows the WebSocket topic name and
 * touches SimpMessagingTemplate directly - see EndpointEventPublisher's
 * Javadoc for why that's a deliberate boundary. Builds the DTO here
 * (rather than have callers build it) so the wire shape has exactly one
 * source of truth.
 */
@Service
public class WebSocketEndpointEventPublisher implements EndpointEventPublisher {

    private static final String ENDPOINT_STATUS_TOPIC = "/topic/endpoints/status";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEndpointEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
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
        messagingTemplate.convertAndSend(ENDPOINT_STATUS_TOPIC, event);
    }
}
