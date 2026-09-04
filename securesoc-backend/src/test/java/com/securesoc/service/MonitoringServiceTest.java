package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.dto.monitoring.InternetUsageEventRequest;
import com.securesoc.dto.monitoring.MonitoringIngestResponse;
import com.securesoc.dto.monitoring.NetworkUsageEventRequest;
import com.securesoc.dto.monitoring.UsbEventRequest;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.InternetUsageEvent;
import com.securesoc.entity.NetworkUsageEvent;
import com.securesoc.entity.UsbEvent;
import com.securesoc.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringServiceTest {

    @Mock private LoginEventRepository loginEventRepository;
    @Mock private LogoutEventRepository logoutEventRepository;
    @Mock private RunningAppSnapshotRepository runningAppSnapshotRepository;
    @Mock private UsbEventRepository usbEventRepository;
    @Mock private VpnEventRepository vpnEventRepository;
    @Mock private IdleEventRepository idleEventRepository;
    @Mock private NetworkUsageEventRepository networkUsageEventRepository;
    @Mock private InternetUsageEventRepository internetUsageEventRepository;
    @Mock private DetectionEngine detectionEngine;

    private MonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        monitoringService = new MonitoringService(
            loginEventRepository,
            logoutEventRepository,
            runningAppSnapshotRepository,
            usbEventRepository,
            vpnEventRepository,
            idleEventRepository,
            networkUsageEventRepository,
            internetUsageEventRepository,
            detectionEngine
        );
    }

    @Test
    void recordUsb_persistsEventAndCallsDetectionEngine_withCorrectContext() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("test-host");

        UsbEventRequest request = new UsbEventRequest("test-device", "test-id", "vid", "pid", "CONNECTED");

        MonitoringIngestResponse response = monitoringService.recordUsb(device, request);
        
        assertEquals("USB event recorded.", response.message());

        ArgumentCaptor<UsbEvent> eventCaptor = ArgumentCaptor.forClass(UsbEvent.class);
        verify(usbEventRepository).saveAndFlush(eventCaptor.capture());
        
        UsbEvent persistedEvent = eventCaptor.getValue();
        assertEquals(device, persistedEvent.getEndpoint());
        assertEquals("test-device", persistedEvent.getDeviceName());
        assertEquals("test-id", persistedEvent.getDeviceId());
        assertEquals("vid", persistedEvent.getVendorId());
        assertEquals("pid", persistedEvent.getProductId());
        assertEquals(UsbEvent.Action.CONNECTED, persistedEvent.getAction());
        assertNotNull(persistedEvent.getEventTime());

        ArgumentCaptor<DetectionContext> contextCaptor = ArgumentCaptor.forClass(DetectionContext.class);
        verify(detectionEngine).evaluate(contextCaptor.capture());
        
        DetectionContext context = contextCaptor.getValue();
        assertEquals("USB_EVENT", context.eventSource());
        assertEquals(device.getId(), context.endpointId());
        assertNull(context.userId());
        assertEquals(persistedEvent.getEventTime(), context.occurredAt());
        assertEquals(persistedEvent, context.event());
    }

    // -----------------------------------------------------------------
    // sampledAt (agent collection time) vs recordedAt (backend ingestion
    // time) - Network Telemetry Phase 1
    // -----------------------------------------------------------------

    @Test
    void recordNetworkUsage_persistsSampledAtFromRequest_recordedAtIsBackendGenerated() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("test-host");

        // Deliberately in the past and distinct from "now" so the test can
        // tell sampledAt (agent time) and recordedAt (backend time) apart.
        Instant sampledAt = Instant.now().minusSeconds(120);
        NetworkUsageEventRequest request = new NetworkUsageEventRequest(1000L, 2000L, null, sampledAt);

        Instant before = Instant.now();
        MonitoringIngestResponse response = monitoringService.recordNetworkUsage(device, request);
        Instant after = Instant.now();

        assertEquals("Network usage recorded.", response.message());

        ArgumentCaptor<NetworkUsageEvent> captor = ArgumentCaptor.forClass(NetworkUsageEvent.class);
        verify(networkUsageEventRepository).save(captor.capture());
        NetworkUsageEvent persisted = captor.getValue();

        assertEquals(sampledAt, persisted.getSampledAt());
        assertNotNull(persisted.getRecordedAt());
        assertFalse(persisted.getRecordedAt().isBefore(before));
        assertFalse(persisted.getRecordedAt().isAfter(after));
        assertNotEquals(persisted.getSampledAt(), persisted.getRecordedAt());
    }

    @Test
    void recordNetworkUsage_withNullSampledAt_persistsNullRatherThanSynthesizingIt() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());

        // Simulates a pre-upgrade agent, or a payload already sitting in
        // its offline queue before sampledAt existed.
        NetworkUsageEventRequest request = new NetworkUsageEventRequest(500L, 700L, null, null);

        monitoringService.recordNetworkUsage(device, request);

        ArgumentCaptor<NetworkUsageEvent> captor = ArgumentCaptor.forClass(NetworkUsageEvent.class);
        verify(networkUsageEventRepository).save(captor.capture());
        NetworkUsageEvent persisted = captor.getValue();

        assertNull(persisted.getSampledAt());
        assertNotNull(persisted.getRecordedAt());
    }

    @Test
    void recordInternetUsage_persistsSampledAtFromRequest_recordedAtIsBackendGenerated() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("test-host");

        Instant sampledAt = Instant.now().minusSeconds(90);
        InternetUsageEventRequest request = new InternetUsageEventRequest(
            BigDecimal.valueOf(1.5), BigDecimal.valueOf(2.5), 60, sampledAt);

        Instant before = Instant.now();
        MonitoringIngestResponse response = monitoringService.recordInternetUsage(device, request);
        Instant after = Instant.now();

        assertEquals("Internet usage recorded.", response.message());

        ArgumentCaptor<InternetUsageEvent> captor = ArgumentCaptor.forClass(InternetUsageEvent.class);
        verify(internetUsageEventRepository).save(captor.capture());
        InternetUsageEvent persisted = captor.getValue();

        assertEquals(sampledAt, persisted.getSampledAt());
        assertNotNull(persisted.getRecordedAt());
        assertFalse(persisted.getRecordedAt().isBefore(before));
        assertFalse(persisted.getRecordedAt().isAfter(after));
        assertNotEquals(persisted.getSampledAt(), persisted.getRecordedAt());
    }

    @Test
    void recordInternetUsage_withNullSampledAt_persistsNullRatherThanSynthesizingIt() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());

        InternetUsageEventRequest request = new InternetUsageEventRequest(
            BigDecimal.valueOf(0.5), BigDecimal.valueOf(0.7), 60, null);

        monitoringService.recordInternetUsage(device, request);

        ArgumentCaptor<InternetUsageEvent> captor = ArgumentCaptor.forClass(InternetUsageEvent.class);
        verify(internetUsageEventRepository).save(captor.capture());
        InternetUsageEvent persisted = captor.getValue();

        assertNull(persisted.getSampledAt());
        assertNotNull(persisted.getRecordedAt());
    }
}
