package com.securesoc.service;

import com.securesoc.dto.EndpointStatusEvent;
import com.securesoc.entity.Department;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.Laboratory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * No test file previously existed for WebSocketEndpointEventPublisher.
 * Covers the entity->DTO mapping (unchanged from before RBAC Phase 3) and
 * that it now delegates to ScopedWebSocketDelivery with the endpoint's
 * own ID as the scope key, instead of calling SimpMessagingTemplate
 * directly.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketEndpointEventPublisherTest {

    @Mock
    private ScopedWebSocketDelivery scopedDelivery;

    private WebSocketEndpointEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WebSocketEndpointEventPublisher(scopedDelivery);
    }

    @Test
    void publishStatusChange_mapsEntityFieldsAndDelegatesWithEndpointIdAsScopeKey() {
        UUID endpointId = UUID.randomUUID();
        Department dept = new Department();
        dept.setId(UUID.randomUUID());
        Laboratory lab = new Laboratory();
        lab.setId(UUID.randomUUID());
        lab.setName("Lab 1");
        lab.setDepartment(dept);

        EndpointDevice device = new EndpointDevice();
        device.setId(endpointId);
        device.setHostname("LAB-PC-01");
        device.setStatus(EndpointDevice.Status.ONLINE);
        device.setLastHeartbeatAt(Instant.now());
        device.setLab(lab);

        publisher.publishStatusChange(device);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(scopedDelivery).deliverToAuthorizedUsers(eq("/queue/endpoints/status"), payloadCaptor.capture(), eq(endpointId));

        EndpointStatusEvent event = (EndpointStatusEvent) payloadCaptor.getValue();
        assertEquals(endpointId, event.endpointId());
        assertEquals("LAB-PC-01", event.hostname());
        assertEquals("ONLINE", event.status());
        assertEquals(device.getLastHeartbeatAt(), event.lastHeartbeatAt());
        assertEquals("Lab 1", event.labName());
    }

    @Test
    void publishStatusChange_nullLab_mapsLabNameAsNull() {
        UUID endpointId = UUID.randomUUID();
        EndpointDevice device = new EndpointDevice();
        device.setId(endpointId);
        device.setHostname("LAB-PC-02");
        device.setStatus(EndpointDevice.Status.OFFLINE);

        publisher.publishStatusChange(device);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(scopedDelivery).deliverToAuthorizedUsers(any(), payloadCaptor.capture(), eq(endpointId));

        EndpointStatusEvent event = (EndpointStatusEvent) payloadCaptor.getValue();
        assertNull(event.labName());
    }
}
