package com.securesoc.service;

import com.securesoc.dto.EndpointStatusEvent;
import com.securesoc.entity.Department;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.Laboratory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

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
    void publishStatusChange_defersDeliveryUntilTransactionCommits() {
        UUID endpointId = UUID.randomUUID();
        EndpointDevice device = new EndpointDevice();
        device.setId(endpointId);
        device.setHostname("LAB-PC-03");
        device.setStatus(EndpointDevice.Status.ONLINE);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishStatusChange(device);

            verify(scopedDelivery, never()).deliverToAuthorizedUsers(any(), any(), any());
            assertTrue(TransactionSynchronizationManager.isSynchronizationActive());

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(scopedDelivery).deliverToAuthorizedUsers(
                eq("/queue/endpoints/status"),
                any(EndpointStatusEvent.class),
                eq(endpointId)
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void publishStatusChange_isNotDeliveredWhenTransactionRollsBack() {
        UUID endpointId = UUID.randomUUID();
        EndpointDevice device = new EndpointDevice();
        device.setId(endpointId);
        device.setHostname("LAB-PC-04");
        device.setStatus(EndpointDevice.Status.OFFLINE);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishStatusChange(device);

            // A rollback does not invoke afterCommit(), so no delivery occurs.
            assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty());
            verify(scopedDelivery, never()).deliverToAuthorizedUsers(any(), any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(scopedDelivery, never()).deliverToAuthorizedUsers(any(), any(), any());
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
