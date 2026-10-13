package com.securesoc.service;

import com.securesoc.config.AgentProperties;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.repository.EndpointDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for EndpointOfflineSweeper: which endpoints are flipped, what
 * is published, and that the production schedule is still declared. (The
 * integration-test contexts run with scheduling disabled, so this is the
 * guard that the job itself is intact.)
 */
@ExtendWith(MockitoExtension.class)
class EndpointOfflineSweeperTest {

    private static final long TIMEOUT_SECONDS = 60;

    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;

    @Mock
    private EndpointEventPublisher endpointEventPublisher;

    private EndpointOfflineSweeper sweeper;

    @BeforeEach
    void setUp() {
        sweeper = new EndpointOfflineSweeper(
            endpointDeviceRepository,
            new AgentProperties("unused-in-this-test", TIMEOUT_SECONDS),
            endpointEventPublisher);
    }

    @Test
    void staleOnlineEndpoints_areMarkedOfflineSavedAndPublished() {
        EndpointDevice first = onlineDevice();
        EndpointDevice second = onlineDevice();
        when(endpointDeviceRepository.findByStatusAndLastHeartbeatAtBefore(
            any(EndpointDevice.Status.class), any(Instant.class))).thenReturn(List.of(first, second));
        when(endpointDeviceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();
        sweeper.sweep();
        Instant after = Instant.now();

        assertEquals(EndpointDevice.Status.OFFLINE, first.getStatus());
        assertEquals(EndpointDevice.Status.OFFLINE, second.getStatus());
        verify(endpointEventPublisher).publishStatusChange(first);
        verify(endpointEventPublisher).publishStatusChange(second);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(endpointDeviceRepository)
            .findByStatusAndLastHeartbeatAtBefore(any(EndpointDevice.Status.class), cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(before.minusSeconds(TIMEOUT_SECONDS)));
        assertFalse(cutoff.getValue().isAfter(after.minusSeconds(TIMEOUT_SECONDS)));
    }

    @Test
    void queriesOnlyForOnlineEndpoints() {
        when(endpointDeviceRepository.findByStatusAndLastHeartbeatAtBefore(
            any(EndpointDevice.Status.class), any(Instant.class))).thenReturn(List.of());

        sweeper.sweep();

        verify(endpointDeviceRepository).findByStatusAndLastHeartbeatAtBefore(
            org.mockito.ArgumentMatchers.eq(EndpointDevice.Status.ONLINE), any(Instant.class));
    }

    @Test
    void noStaleEndpoints_savesAndPublishesNothing() {
        when(endpointDeviceRepository.findByStatusAndLastHeartbeatAtBefore(
            any(EndpointDevice.Status.class), any(Instant.class))).thenReturn(List.of());

        sweeper.sweep();

        verify(endpointDeviceRepository, never()).saveAll(any());
        verifyNoInteractions(endpointEventPublisher);
    }

    @Test
    void sweepIsStillScheduledEvery15SecondsAndTransactional() throws NoSuchMethodException {
        var method = EndpointOfflineSweeper.class.getMethod("sweep");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "sweep() must stay @Scheduled - production offline detection depends on it");
        assertEquals(15_000L, scheduled.fixedRate());
        assertTrue(method.isAnnotationPresent(Transactional.class));
    }

    private static EndpointDevice onlineDevice() {
        EndpointDevice device = new EndpointDevice();
        device.setStatus(EndpointDevice.Status.ONLINE);
        device.setLastHeartbeatAt(Instant.now().minusSeconds(TIMEOUT_SECONDS * 5));
        return device;
    }
}
