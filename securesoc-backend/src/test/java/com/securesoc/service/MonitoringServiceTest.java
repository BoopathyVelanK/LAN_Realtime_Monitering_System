package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.dto.PageResponse;
import com.securesoc.dto.monitoring.IdleEventRequest;
import com.securesoc.dto.monitoring.InternetUsageEventRequest;
import com.securesoc.dto.monitoring.InternetUsageEventResponse;
import com.securesoc.dto.monitoring.LoginEventResponse;
import com.securesoc.dto.monitoring.LogoutEventResponse;
import com.securesoc.dto.monitoring.MonitoringIngestResponse;
import com.securesoc.dto.monitoring.NetworkUsageEventRequest;
import com.securesoc.dto.monitoring.NetworkUsageEventResponse;
import com.securesoc.dto.monitoring.RunningAppSnapshotResponse;
import com.securesoc.dto.monitoring.UsbEventRequest;
import com.securesoc.dto.monitoring.UsbEventResponse;
import com.securesoc.dto.monitoring.VpnEventResponse;
import com.securesoc.dto.monitoring.IdleEventResponse;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.IdleEvent;
import com.securesoc.entity.InternetUsageEvent;
import com.securesoc.entity.LoginEvent;
import com.securesoc.entity.LogoutEvent;
import com.securesoc.entity.NetworkUsageEvent;
import com.securesoc.entity.RunningAppSnapshot;
import com.securesoc.entity.UsbEvent;
import com.securesoc.entity.VpnEvent;
import com.securesoc.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private FacultyScopeService facultyScopeService;

    private MonitoringService monitoringService;

    private UUID callerId;
    private UUID endpointId;
    private Pageable pageable;

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
            detectionEvaluationExecutor,
            facultyScopeService
        );
        callerId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
        pageable = PageRequest.of(0, 20);
    }

    private EndpointDevice endpointWithHostname(UUID id) {
        EndpointDevice device = new EndpointDevice();
        device.setId(id);
        device.setHostname("test-host");
        return device;
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

    // =====================================================================
    // RBAC / Faculty scope (Phase 2) - GET /monitoring/**
    //
    // USB gets the full matrix (admin bypass, faculty-own-endpoint success,
    // cross-scope denial, omitted-endpointId lab-scoped query, no-labs
    // empty page) as the reference case. The remaining seven monitoring
    // types each get the two tests that matter most for IDOR/BOLA: cross-
    // scope endpointId denial, and omitted-endpointId never falling back
    // to the global fleet feed.
    // =====================================================================

    // --- USB (full matrix) ------------------------------------------------

    @Test
    void listUsbEvents_admin_noEndpointId_returnsUnfilteredFleetFeed() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(true);
        UsbEvent event = new UsbEvent();
        event.setEndpoint(endpointWithHostname(endpointId));
        when(usbEventRepository.findAllByOrderByEventTimeDesc(pageable))
            .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<UsbEventResponse> result = monitoringService.listUsbEvents(null, pageable, callerId);

        assertEquals(1, result.content().size());
        verify(facultyScopeService, never()).accessibleLaboratoryIds(any());
    }

    @Test
    void listUsbEvents_facultyOwnEndpoint_succeeds() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(callerId, endpointId)).thenReturn(true);
        UsbEvent event = new UsbEvent();
        event.setEndpoint(endpointWithHostname(endpointId));
        when(usbEventRepository.findByEndpoint_IdOrderByEventTimeDesc(endpointId, pageable))
            .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<UsbEventResponse> result = monitoringService.listUsbEvents(endpointId, pageable, callerId);

        assertEquals(1, result.content().size());
    }

    @Test
    void listUsbEvents_facultyAnotherFacultysEndpoint_deniedNeverQueriesData() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(callerId, endpointId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
            () -> monitoringService.listUsbEvents(endpointId, pageable, callerId));

        verify(usbEventRepository, never()).findByEndpoint_IdOrderByEventTimeDesc(any(), any());
    }

    @Test
    void listUsbEvents_facultyOmittedEndpointId_usesLabScopedQueryNeverGlobalFleet() {
        UUID labId = UUID.randomUUID();
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labId));
        UsbEvent event = new UsbEvent();
        event.setEndpoint(endpointWithHostname(endpointId));
        when(usbEventRepository.findByEndpoint_Lab_IdInOrderByEventTimeDesc(eq(Set.of(labId)), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<UsbEventResponse> result = monitoringService.listUsbEvents(null, pageable, callerId);

        assertEquals(1, result.content().size());
        verify(usbEventRepository, never()).findAllByOrderByEventTimeDesc(any());
    }

    @Test
    void listUsbEvents_facultyNoAssignedLabs_returnsEmptyPageNotGlobalFleet() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of());

        PageResponse<UsbEventResponse> result = monitoringService.listUsbEvents(null, pageable, callerId);

        assertTrue(result.content().isEmpty());
        verifyNoInteractions(usbEventRepository);
    }

    // --- Login --------------------------------------------------------

    @Test
    void listLoginEvents_facultyAnotherFacultysEndpoint_denied() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(callerId, endpointId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
            () -> monitoringService.listLoginEvents(endpointId, pageable, callerId));

        verify(loginEventRepository, never()).findByEndpoint_IdOrderByLoginTimeDesc(any(), any());
    }

    @Test
    void listLoginEvents_facultyOmittedEndpointId_usesLabScopedQueryNeverGlobalFleet() {
        UUID labId = UUID.randomUUID();
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labId));
        LoginEvent event = new LoginEvent();
        event.setEndpoint(endpointWithHostname(endpointId));
        when(loginEventRepository.findByEndpoint_Lab_IdInOrderByLoginTimeDesc(eq(Set.of(labId)), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<LoginEventResponse> result = monitoringService.listLoginEvents(null, pageable, callerId);

        assertEquals(1, result.content().size());
        verify(loginEventRepository, never()).findAllByOrderByLoginTimeDesc(any());
    }

    // --- Logout -------------------------------------------------------

    @Test
    void listLogoutEvents_facultyAnotherFacultysEndpoint_denied() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(callerId, endpointId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
            () -> monitoringService.listLogoutEvents(endpointId, pageable, callerId));

        verify(logoutEventRepository, never()).findByEndpoint_IdOrderByLogoutTimeDesc(any(), any());
    }

    @Test
    void listLogoutEvents_facultyOmittedEndpointId_usesLabScopedQueryNeverGlobalFleet() {
        UUID labId = UUID.randomUUID();
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labId));
        LogoutEvent event = new LogoutEvent();
        event.setEndpoint(endpointWithHostname(endpointId));
        when(logoutEventRepository.findByEndpoint_Lab_IdInOrderByLogoutTimeDesc(eq(Set.of(labId)), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<LogoutEventResponse> result = monitoringService.listLogoutEvents(null, pageable, callerId);

        assertEquals(1, result.content().size());
        verify(logoutEventRepository, never()).findAllByOrderByLogoutTimeDesc(any());
    }

    // --- VPN ------------------------------------------------------------

    @Test
    void listVpnEvents_facultyAnotherFacultysEndpoint_denied() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(callerId, endpointId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
            () -> monitoringService.listVpnEvents(endpointId, pageable, callerId));

        verify(vpnEventRepository, never()).findByEndpoint_IdOrderByDetectedAtDesc(any(), any());
    }

    @Test
    void listVpnEvents_facultyOmittedEndpointId_usesLabScopedQueryNeverGlobalFleet() {
        UUID labId = UUID.randomUUID();
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labId));
        VpnEvent event = new VpnEvent();
        event.setEndpoint(endpointWithHostname(endpointId));
        when(vpnEventRepository.findByEndpoint_Lab_IdInOrderByDetectedAtDesc(eq(Set.of(labId)), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<VpnEventResponse> result = monitoringService.listVpnEvents(null, pageable, callerId);

        assertEquals(1, result.content().size());
        verify(vpnEventRepository, never()).findAllByOrderByDetectedAtDesc(any());
    }

    // --- Idle -----------------------------------------------------------

    @Test
    void listIdleEvents_facultyAnotherFacultysEndpoint_denied() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(callerId, endpointId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
            () -> monitoringService.listIdleEvents(endpointId, pageable, callerId));

        verify(idleEventRepository, never()).findByEndpoint_IdOrderByRecordedAtDesc(any(), any());
    }

    @Test
    void listIdleEvents_facultyOmittedEndpointId_usesLabScopedQueryNeverGlobalFleet() {
        UUID labId = UUID.randomUUID();
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labId));
        IdleEvent event = new IdleEvent();
        event.setEndpoint(endpointWithHostname(endpointId));
        when(idleEventRepository.findByEndpoint_Lab_IdInOrderByRecordedAtDesc(eq(Set.of(labId)), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<IdleEventResponse> result = monitoringService.listIdleEvents(null, pageable, callerId);

        assertEquals(1, result.content().size());
        verify(idleEventRepository, never()).findAllByOrderByRecordedAtDesc(any());
    }

    // --- Network usage ----------------------------------------------------

    @Test
    void listNetworkUsageEvents_facultyAnotherFacultysEndpoint_denied() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(callerId, endpointId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
            () -> monitoringService.listNetworkUsageEvents(endpointId, pageable, callerId));

        verify(networkUsageEventRepository, never()).findByEndpoint_IdOrderByRecordedAtDesc(any(), any());
    }

    @Test
    void listNetworkUsageEvents_facultyOmittedEndpointId_usesLabScopedQueryNeverGlobalFleet() {
        UUID labId = UUID.randomUUID();
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labId));
        NetworkUsageEvent event = new NetworkUsageEvent();
        event.setEndpoint(endpointWithHostname(endpointId));
        when(networkUsageEventRepository.findByEndpoint_Lab_IdInOrderByRecordedAtDesc(eq(Set.of(labId)), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<NetworkUsageEventResponse> result =
            monitoringService.listNetworkUsageEvents(null, pageable, callerId);

        assertEquals(1, result.content().size());
        verify(networkUsageEventRepository, never()).findAllByOrderByRecordedAtDesc(any());
    }

    // --- Internet usage --------------------------------------------------

    @Test
    void listInternetUsageEvents_facultyAnotherFacultysEndpoint_denied() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(callerId, endpointId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
            () -> monitoringService.listInternetUsageEvents(endpointId, pageable, callerId));

        verify(internetUsageEventRepository, never()).findByEndpoint_IdOrderByRecordedAtDesc(any(), any());
    }

    @Test
    void listInternetUsageEvents_facultyOmittedEndpointId_usesLabScopedQueryNeverGlobalFleet() {
        UUID labId = UUID.randomUUID();
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labId));
        InternetUsageEvent event = new InternetUsageEvent();
        event.setEndpoint(endpointWithHostname(endpointId));
        when(internetUsageEventRepository.findByEndpoint_Lab_IdInOrderByRecordedAtDesc(eq(Set.of(labId)), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(event)));

        PageResponse<InternetUsageEventResponse> result =
            monitoringService.listInternetUsageEvents(null, pageable, callerId);

        assertEquals(1, result.content().size());
        verify(internetUsageEventRepository, never()).findAllByOrderByRecordedAtDesc(any());
    }

    // --- Running applications ----------------------------------------------

    @Test
    void listRunningAppSnapshots_facultyAnotherFacultysEndpoint_denied() {
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.canAccessEndpoint(callerId, endpointId)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
            () -> monitoringService.listRunningAppSnapshots(endpointId, pageable, callerId));

        verify(runningAppSnapshotRepository, never()).findByEndpoint_IdOrderByCapturedAtDesc(any(), any());
    }

    @Test
    void listRunningAppSnapshots_facultyOmittedEndpointId_usesLabScopedQueryNeverGlobalFleet() {
        UUID labId = UUID.randomUUID();
        when(facultyScopeService.isGlobalScope(callerId)).thenReturn(false);
        when(facultyScopeService.accessibleLaboratoryIds(callerId)).thenReturn(Set.of(labId));
        RunningAppSnapshot snapshot = new RunningAppSnapshot();
        snapshot.setEndpoint(endpointWithHostname(endpointId));
        when(runningAppSnapshotRepository.findByEndpoint_Lab_IdInOrderByCapturedAtDesc(eq(Set.of(labId)), eq(pageable)))
            .thenReturn(new PageImpl<>(List.of(snapshot)));

        PageResponse<RunningAppSnapshotResponse> result =
            monitoringService.listRunningAppSnapshots(null, pageable, callerId);

        assertEquals(1, result.content().size());
        verify(runningAppSnapshotRepository, never()).findAllByOrderByCapturedAtDesc(any());
    }
}
