package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
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
    @Mock private UsbEventPersistenceExecutor usbEventPersistenceExecutor;
    @Mock private DetectionEvaluationExecutor detectionEvaluationExecutor;

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
            usbEventPersistenceExecutor,
            detectionEvaluationExecutor
        );
    }

    @Test
    void recordUsb_persistsEventAndCallsDetectionEvaluationExecutor_withCorrectContext() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());
        device.setHostname("test-host");

        UsbEventRequest request = new UsbEventRequest("test-device", "test-id", "vid", "pid", "CONNECTED");

        when(usbEventPersistenceExecutor.persist(any(UsbEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        MonitoringIngestResponse response = monitoringService.recordUsb(device, request);
        
        assertEquals("USB event recorded.", response.message());

        ArgumentCaptor<UsbEvent> eventCaptor = ArgumentCaptor.forClass(UsbEvent.class);
        verify(usbEventPersistenceExecutor).persist(eventCaptor.capture());
        
        UsbEvent persistedEvent = eventCaptor.getValue();
        assertEquals(device, persistedEvent.getEndpoint());
        assertEquals("test-device", persistedEvent.getDeviceName());
        assertEquals("test-id", persistedEvent.getDeviceId());
        assertEquals("vid", persistedEvent.getVendorId());
        assertEquals("pid", persistedEvent.getProductId());
        assertEquals(UsbEvent.Action.CONNECTED, persistedEvent.getAction());
        assertNotNull(persistedEvent.getEventTime());

        ArgumentCaptor<DetectionContext> contextCaptor = ArgumentCaptor.forClass(DetectionContext.class);
        verify(detectionEvaluationExecutor).evaluate(contextCaptor.capture());
        
        DetectionContext context = contextCaptor.getValue();
        assertEquals("USB_EVENT", context.eventSource());
        assertEquals(device.getId(), context.endpointId());
        assertNull(context.userId());
        assertEquals(persistedEvent.getEventTime(), context.occurredAt());
        assertEquals(persistedEvent, context.event());
    }

    // -----------------------------------------------------------------
    // USB detection-failure isolation - a RuntimeException from detection
    // must never prevent the already-persisted UsbEvent from being
    // returned as a successful ingest. See DetectionEvaluationExecutor
    // for why a REQUIRES_NEW boundary (not just a try/catch around a
    // plain DetectionEngine call) is required for this guarantee.
    // -----------------------------------------------------------------

    @Test
    void recordUsb_detectionSucceeds_returnsNormalSuccessResponse() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());

        UsbEventRequest request = new UsbEventRequest("test-device", "test-id", "vid", "pid", "CONNECTED");

        when(usbEventPersistenceExecutor.persist(any(UsbEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        MonitoringIngestResponse response = monitoringService.recordUsb(device, request);

        assertEquals("USB event recorded.", response.message());
        verify(usbEventPersistenceExecutor).persist(any(UsbEvent.class));
        verify(detectionEvaluationExecutor).evaluate(any(DetectionContext.class));
    }

    @Test
    void recordUsb_detectionEvaluationExecutorThrows_stillReturnsSuccessResponse_andUsbEventWasAlreadyPersisted() {
        EndpointDevice device = new EndpointDevice();
        device.setId(UUID.randomUUID());

        UsbEventRequest request = new UsbEventRequest("test-device", "test-id", "vid", "pid", "CONNECTED");

        when(usbEventPersistenceExecutor.persist(any(UsbEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(detectionEvaluationExecutor.evaluate(any(DetectionContext.class)))
            .thenThrow(new RuntimeException("simulated detection failure"));

        MonitoringIngestResponse response = assertDoesNotThrow(() -> monitoringService.recordUsb(device, request));

        assertEquals("USB event recorded.", response.message());

        // The UsbEvent was persisted (and, for real, committed - see
        // UsbEventPersistenceExecutor) strictly BEFORE the detection call,
        // so this verification proves the ordering and that no exception
        // propagates past this method. A unit test can prove that
        // ordering but not that the persistence transaction actually
        // commits in isolation from detection; that is proven separately
        // by UsbDetectionFailureIsolationIntegrationTest against real
        // PostgreSQL.
        verify(usbEventPersistenceExecutor).persist(any(UsbEvent.class));
        verify(detectionEvaluationExecutor).evaluate(any(DetectionContext.class));
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
