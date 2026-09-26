package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.dto.PageResponse;
import com.securesoc.dto.monitoring.*;
import com.securesoc.entity.*;
import com.securesoc.entity.UsbEvent.Action;
import com.securesoc.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Handles both ingestion (POST /monitoring/**, from agent.py - see the
 * class-level notes on each record* method below) and reads (GET
 * /monitoring/**, for the frontend dashboard - see the list* methods added
 * in Phase 4B) for monitoring data. Still no policy/risk decisions either
 * way - that's Phase 4's detection engine, not this service.
 *
 * Every ingest method takes the already-authenticated EndpointDevice
 * (resolved by AgentTokenAuthFilter, same pattern as
 * AgentService.heartbeat) rather than re-resolving it from a token. Every
 * read method takes an optional endpointId filter and a Pageable, and is
 * @Transactional(readOnly = true) so each mapped row's lazily-loaded
 * entity.getEndpoint().getHostname() resolves within the same Hibernate
 * session instead of throwing LazyInitializationException.
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final LoginEventRepository loginEventRepository;
    private final LogoutEventRepository logoutEventRepository;
    private final RunningAppSnapshotRepository runningAppSnapshotRepository;
    private final UsbEventRepository usbEventRepository;
    private final VpnEventRepository vpnEventRepository;
    private final IdleEventRepository idleEventRepository;
    private final NetworkUsageEventRepository networkUsageEventRepository;
    private final InternetUsageEventRepository internetUsageEventRepository;
    private final UsbEventPersistenceExecutor usbEventPersistenceExecutor;
    private final VpnEventPersistenceExecutor vpnEventPersistenceExecutor;
    private final DetectionEvaluationExecutor detectionEvaluationExecutor;
    private final FacultyScopeService facultyScopeService;

    public MonitoringService(
        LoginEventRepository loginEventRepository,
        LogoutEventRepository logoutEventRepository,
        RunningAppSnapshotRepository runningAppSnapshotRepository,
        UsbEventRepository usbEventRepository,
        VpnEventRepository vpnEventRepository,
        IdleEventRepository idleEventRepository,
        NetworkUsageEventRepository networkUsageEventRepository,
        InternetUsageEventRepository internetUsageEventRepository,
        UsbEventPersistenceExecutor usbEventPersistenceExecutor,
        VpnEventPersistenceExecutor vpnEventPersistenceExecutor,
        DetectionEvaluationExecutor detectionEvaluationExecutor,
        FacultyScopeService facultyScopeService
    ) {
        this.loginEventRepository = loginEventRepository;
        this.logoutEventRepository = logoutEventRepository;
        this.runningAppSnapshotRepository = runningAppSnapshotRepository;
        this.usbEventRepository = usbEventRepository;
        this.vpnEventRepository = vpnEventRepository;
        this.idleEventRepository = idleEventRepository;
        this.networkUsageEventRepository = networkUsageEventRepository;
        this.internetUsageEventRepository = internetUsageEventRepository;
        this.usbEventPersistenceExecutor = usbEventPersistenceExecutor;
        this.vpnEventPersistenceExecutor = vpnEventPersistenceExecutor;
        this.detectionEvaluationExecutor = detectionEvaluationExecutor;
        this.facultyScopeService = facultyScopeService;
    }

    // -----------------------------------------------------------------
    // Ingest (Phase 3 - unchanged)
    // -----------------------------------------------------------------

    @Transactional
    public MonitoringIngestResponse recordLogin(EndpointDevice device, LoginEventRequest request) {
        LoginEvent event = new LoginEvent();
        event.setEndpoint(device);
        event.setOsUsername(request.osUsername());
        event.setSessionId(request.sessionId());
        event.setLoginTime(request.loginTime() != null ? request.loginTime() : Instant.now());
        loginEventRepository.save(event);
        return MonitoringIngestResponse.ok("Login event recorded.");
    }

    @Transactional
    public MonitoringIngestResponse recordLogout(EndpointDevice device, LogoutEventRequest request) {
        LogoutEvent event = new LogoutEvent();
        event.setEndpoint(device);
        event.setOsUsername(request.osUsername());
        event.setSessionId(request.sessionId());
        event.setLogoutTime(request.logoutTime() != null ? request.logoutTime() : Instant.now());
        logoutEventRepository.save(event);
        return MonitoringIngestResponse.ok("Logout event recorded.");
    }

    @Transactional
    public MonitoringIngestResponse recordRunningApps(EndpointDevice device, RunningAppsRequest request) {
        RunningAppSnapshot snapshot = new RunningAppSnapshot();
        snapshot.setEndpoint(device);

        List<RunningAppsRequest.AppEntry> entries = request.applications();
        for (RunningAppsRequest.AppEntry entry : entries) {
            RunningApp app = new RunningApp();
            app.setSnapshot(snapshot);
            app.setProcessName(entry.processName());
            app.setWindowTitle(entry.windowTitle());
            app.setPid(entry.pid());
            snapshot.getApps().add(app);
        }

        runningAppSnapshotRepository.save(snapshot); // cascades to RunningApp rows
        return MonitoringIngestResponse.ok("Recorded " + entries.size() + " running application(s).");
    }

    // Deliberately NOT @Transactional at this level - see
    // UsbEventPersistenceExecutor's javadoc. Persistence and detection
    // must run as two independent, sequential, separately-committed
    // transactions, not as one shared transaction wrapping both: a
    // detector relying on a database-backed count (UsbEventDetector's
    // threshold query) must be able to see the very row that triggered
    // it, which requires that row to already be committed - not just
    // flushed - before detection's own transaction begins.
    public MonitoringIngestResponse recordUsb(EndpointDevice device, UsbEventRequest request) {
        UsbEvent event = new UsbEvent();
        event.setEndpoint(device);
        event.setDeviceName(request.deviceName());
        event.setDeviceId(request.deviceId());
        event.setVendorId(request.vendorId());
        event.setProductId(request.productId());

        if (request.action() != null) {
            try {
                event.setAction(Action.valueOf(request.action().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Unknown action string - store the event without a
                // typed action rather than rejecting the whole ingest;
                // Phase 4 detection rules can flag null-action rows.
            }
        }

        // Persisted and COMMITTED independently, in its own transaction,
        // strictly before detection runs - see UsbEventPersistenceExecutor's
        // javadoc for why detection otherwise cannot see the very event it
        // is meant to evaluate under PostgreSQL READ COMMITTED isolation.
        UsbEvent persisted = usbEventPersistenceExecutor.persist(event);

        DetectionContext context = new DetectionContext(
            "USB_EVENT",
            device.getId(),
            null,
            persisted.getEventTime(),
            persisted
        );

        // Detection/alert/risk processing runs in its own isolated
        // (REQUIRES_NEW) transaction - see DetectionEvaluationExecutor's
        // javadoc for why a plain call into DetectionEngine here would
        // not be safe. Any failure there must never cost us the USB
        // telemetry event already committed above; this mirrors how
        // AuthService.login() isolates its own DetectionEngine.evaluate()
        // call from its own critical response.
        try {
            detectionEvaluationExecutor.evaluate(context);
        } catch (RuntimeException detectionFailure) {
            log.error("Detection engine failed while evaluating a USB_EVENT for endpoint {}. "
                + "The USB telemetry event was already persisted and is unaffected.",
                device.getId(), detectionFailure);
        }

        return MonitoringIngestResponse.ok("USB event recorded.");
    }

    // Deliberately NOT @Transactional at this level - see
    // VpnEventPersistenceExecutor's javadoc. Persistence and detection
    // must run as two independent, sequential, separately-committed
    // transactions, not as one shared transaction wrapping both: a
    // detector relying on a database-backed count (VpnEventDetector's
    // threshold query) must be able to see the very row that triggered
    // it, which requires that row to already be committed - not just
    // flushed - before detection's own transaction begins.
    public MonitoringIngestResponse recordVpn(EndpointDevice device, VpnEventRequest request) {
        VpnEvent event = new VpnEvent();
        event.setEndpoint(device);
        event.setAdapterName(request.adapterName());
        event.setActive(request.active());

        // Persisted and COMMITTED independently, in its own transaction,
        // strictly before detection runs - see VpnEventPersistenceExecutor's
        // javadoc for why detection otherwise cannot see the very event it
        // is meant to evaluate under PostgreSQL READ COMMITTED isolation.
        VpnEvent persisted = vpnEventPersistenceExecutor.persist(event);

        DetectionContext context = new DetectionContext(
            "VPN_EVENT",
            device.getId(),
            null,
            persisted.getDetectedAt(),
            null
        );

        // Detection/alert/risk processing runs in its own isolated
        // (REQUIRES_NEW) transaction - see DetectionEvaluationExecutor's
        // javadoc for why a plain call into DetectionEngine here would
        // not be safe. Any failure there must never cost us the VPN
        // telemetry event already committed above; this mirrors how
        // MonitoringService.recordUsb() isolates its own DetectionEngine
        // call from its own critical response.
        try {
            detectionEvaluationExecutor.evaluate(context);
        } catch (RuntimeException detectionFailure) {
            log.error("Detection engine failed while evaluating a VPN_EVENT for endpoint {}. "
                + "The VPN telemetry event was already persisted and is unaffected.",
                device.getId(), detectionFailure);
        }

        return MonitoringIngestResponse.ok("VPN status recorded.");
    }

    @Transactional
    public MonitoringIngestResponse recordIdle(EndpointDevice device, IdleEventRequest request) {
        IdleEvent event = new IdleEvent();
        event.setEndpoint(device);
        event.setIdleSeconds(request.idleSeconds());
        idleEventRepository.save(event);
        return MonitoringIngestResponse.ok("Idle time recorded.");
    }

    @Transactional
    public MonitoringIngestResponse recordNetworkUsage(EndpointDevice device, NetworkUsageEventRequest request) {
        NetworkUsageEvent event = new NetworkUsageEvent();
        event.setEndpoint(device);
        event.setBytesSent(request.bytesSent() != null ? request.bytesSent() : 0L);
        event.setBytesReceived(request.bytesReceived() != null ? request.bytesReceived() : 0L);
        event.setInterfaceName(request.interfaceName());
        // Persisted as-is, including null (an agent build that predates
        // sampledAt, or a payload already queued offline before this
        // upgrade) - recordedAt (below, via the entity's own default)
        // still always reflects real backend ingestion time regardless.
        event.setSampledAt(request.sampledAt());
        networkUsageEventRepository.save(event);
        return MonitoringIngestResponse.ok("Network usage recorded.");
    }

    @Transactional
    public MonitoringIngestResponse recordInternetUsage(EndpointDevice device, InternetUsageEventRequest request) {
        InternetUsageEvent event = new InternetUsageEvent();
        event.setEndpoint(device);
        event.setUploadMb(request.uploadMb() != null ? request.uploadMb() : java.math.BigDecimal.ZERO);
        event.setDownloadMb(request.downloadMb() != null ? request.downloadMb() : java.math.BigDecimal.ZERO);
        event.setPeriodSeconds(request.periodSeconds() != null ? request.periodSeconds() : 0);
        // See recordNetworkUsage's comment above - same null-passthrough,
        // same reasoning.
        event.setSampledAt(request.sampledAt());
        internetUsageEventRepository.save(event);
        return MonitoringIngestResponse.ok("Internet usage recorded.");
    }

    // -----------------------------------------------------------------
    // Reads (Phase 4B) - each takes the authenticated callerId (resolved
    // by the controller from the JWT principal) in addition to the
    // existing endpointId/Pageable params. Admin keeps exactly the
    // previous unscoped behavior. A Faculty caller:
    //  - with an explicit endpointId must be authorized for that endpoint
    //    (FacultyScopeService.canAccessEndpoint) or is denied outright -
    //    changing endpointId can never widen access, only be rejected.
    //  - with no endpointId gets a lab-scoped query (see each
    //    repository's findByEndpoint_Lab_IdInOrderBy...), never the
    //    fleet-wide query used for Admin - omitting endpointId can never
    //    leak the global fleet.
    // Newest first always, in every scope.
    // -----------------------------------------------------------------

    /**
     * Shared scope-resolution for every list* method below: decides, from
     * the caller's already-resolved {@link FacultyScopeService} scope,
     * which of three already-scoped repository queries to run. Never
     * fetches an unscoped page and filters it in Java.
     *
     * @throws AccessDeniedException if a non-global caller supplies an
     *         endpointId they are not authorized for.
     */
    private <T> Page<T> resolveScopedPage(
        UUID endpointId,
        UUID callerId,
        Pageable pageable,
        Supplier<Page<T>> findAll,
        Function<UUID, Page<T>> findByEndpoint,
        Function<Set<UUID>, Page<T>> findByLabIds
    ) {
        boolean global = facultyScopeService.isGlobalScope(callerId);
        if (endpointId != null) {
            if (!global && !facultyScopeService.canAccessEndpoint(callerId, endpointId)) {
                throw new AccessDeniedException("Not authorized for endpoint: " + endpointId);
            }
            return findByEndpoint.apply(endpointId);
        }
        if (global) {
            return findAll.get();
        }
        Set<UUID> labIds = facultyScopeService.accessibleLaboratoryIds(callerId);
        return labIds.isEmpty() ? Page.empty(pageable) : findByLabIds.apply(labIds);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoginEventResponse> listLoginEvents(UUID endpointId, Pageable pageable, UUID callerId) {
        Page<LoginEvent> page = resolveScopedPage(endpointId, callerId, pageable,
            () -> loginEventRepository.findAllByOrderByLoginTimeDesc(pageable),
            id -> loginEventRepository.findByEndpoint_IdOrderByLoginTimeDesc(id, pageable),
            labIds -> loginEventRepository.findByEndpoint_Lab_IdInOrderByLoginTimeDesc(labIds, pageable));
        return PageResponse.of(page.map(e -> new LoginEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getOsUsername(), e.getSessionId(), e.getLoginTime(), e.getReceivedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<LogoutEventResponse> listLogoutEvents(UUID endpointId, Pageable pageable, UUID callerId) {
        Page<LogoutEvent> page = resolveScopedPage(endpointId, callerId, pageable,
            () -> logoutEventRepository.findAllByOrderByLogoutTimeDesc(pageable),
            id -> logoutEventRepository.findByEndpoint_IdOrderByLogoutTimeDesc(id, pageable),
            labIds -> logoutEventRepository.findByEndpoint_Lab_IdInOrderByLogoutTimeDesc(labIds, pageable));
        return PageResponse.of(page.map(e -> new LogoutEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getOsUsername(), e.getSessionId(), e.getLogoutTime(), e.getReceivedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<UsbEventResponse> listUsbEvents(UUID endpointId, Pageable pageable, UUID callerId) {
        Page<UsbEvent> page = resolveScopedPage(endpointId, callerId, pageable,
            () -> usbEventRepository.findAllByOrderByEventTimeDesc(pageable),
            id -> usbEventRepository.findByEndpoint_IdOrderByEventTimeDesc(id, pageable),
            labIds -> usbEventRepository.findByEndpoint_Lab_IdInOrderByEventTimeDesc(labIds, pageable));
        return PageResponse.of(page.map(e -> new UsbEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getDeviceName(), e.getDeviceId(), e.getVendorId(), e.getProductId(),
            e.getAction() != null ? e.getAction().name() : null,
            e.getEventTime(), e.getReceivedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<VpnEventResponse> listVpnEvents(UUID endpointId, Pageable pageable, UUID callerId) {
        Page<VpnEvent> page = resolveScopedPage(endpointId, callerId, pageable,
            () -> vpnEventRepository.findAllByOrderByDetectedAtDesc(pageable),
            id -> vpnEventRepository.findByEndpoint_IdOrderByDetectedAtDesc(id, pageable),
            labIds -> vpnEventRepository.findByEndpoint_Lab_IdInOrderByDetectedAtDesc(labIds, pageable));
        return PageResponse.of(page.map(e -> new VpnEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getAdapterName(), e.isActive(), e.getDetectedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<IdleEventResponse> listIdleEvents(UUID endpointId, Pageable pageable, UUID callerId) {
        Page<IdleEvent> page = resolveScopedPage(endpointId, callerId, pageable,
            () -> idleEventRepository.findAllByOrderByRecordedAtDesc(pageable),
            id -> idleEventRepository.findByEndpoint_IdOrderByRecordedAtDesc(id, pageable),
            labIds -> idleEventRepository.findByEndpoint_Lab_IdInOrderByRecordedAtDesc(labIds, pageable));
        return PageResponse.of(page.map(e -> new IdleEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getIdleSeconds(), e.getRecordedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<NetworkUsageEventResponse> listNetworkUsageEvents(UUID endpointId, Pageable pageable, UUID callerId) {
        Page<NetworkUsageEvent> page = resolveScopedPage(endpointId, callerId, pageable,
            () -> networkUsageEventRepository.findAllByOrderByRecordedAtDesc(pageable),
            id -> networkUsageEventRepository.findByEndpoint_IdOrderByRecordedAtDesc(id, pageable),
            labIds -> networkUsageEventRepository.findByEndpoint_Lab_IdInOrderByRecordedAtDesc(labIds, pageable));
        return PageResponse.of(page.map(e -> new NetworkUsageEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getBytesSent(), e.getBytesReceived(), e.getInterfaceName(), e.getSampledAt(), e.getRecordedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<InternetUsageEventResponse> listInternetUsageEvents(UUID endpointId, Pageable pageable, UUID callerId) {
        Page<InternetUsageEvent> page = resolveScopedPage(endpointId, callerId, pageable,
            () -> internetUsageEventRepository.findAllByOrderByRecordedAtDesc(pageable),
            id -> internetUsageEventRepository.findByEndpoint_IdOrderByRecordedAtDesc(id, pageable),
            labIds -> internetUsageEventRepository.findByEndpoint_Lab_IdInOrderByRecordedAtDesc(labIds, pageable));
        return PageResponse.of(page.map(e -> new InternetUsageEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getUploadMb(), e.getDownloadMb(), e.getPeriodSeconds(), e.getSampledAt(), e.getRecordedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<RunningAppSnapshotResponse> listRunningAppSnapshots(UUID endpointId, Pageable pageable, UUID callerId) {
        Page<RunningAppSnapshot> page = resolveScopedPage(endpointId, callerId, pageable,
            () -> runningAppSnapshotRepository.findAllByOrderByCapturedAtDesc(pageable),
            id -> runningAppSnapshotRepository.findByEndpoint_IdOrderByCapturedAtDesc(id, pageable),
            labIds -> runningAppSnapshotRepository.findByEndpoint_Lab_IdInOrderByCapturedAtDesc(labIds, pageable));
        return PageResponse.of(page.map(s -> new RunningAppSnapshotResponse(
            s.getId(), s.getEndpoint().getId(), s.getEndpoint().getHostname(), s.getCapturedAt(),
            s.getApps().stream()
                .map(a -> new RunningAppSnapshotResponse.AppEntry(a.getProcessName(), a.getWindowTitle(), a.getPid()))
                .toList()
        )));
    }
}
