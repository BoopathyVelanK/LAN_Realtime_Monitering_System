package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.dto.PageResponse;
import com.securesoc.dto.monitoring.*;
import com.securesoc.entity.*;
import com.securesoc.entity.UsbEvent.Action;
import com.securesoc.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    private final LoginEventRepository loginEventRepository;
    private final LogoutEventRepository logoutEventRepository;
    private final RunningAppSnapshotRepository runningAppSnapshotRepository;
    private final UsbEventRepository usbEventRepository;
    private final VpnEventRepository vpnEventRepository;
    private final IdleEventRepository idleEventRepository;
    private final NetworkUsageEventRepository networkUsageEventRepository;
    private final InternetUsageEventRepository internetUsageEventRepository;
    private final DetectionEngine detectionEngine;

    public MonitoringService(
        LoginEventRepository loginEventRepository,
        LogoutEventRepository logoutEventRepository,
        RunningAppSnapshotRepository runningAppSnapshotRepository,
        UsbEventRepository usbEventRepository,
        VpnEventRepository vpnEventRepository,
        IdleEventRepository idleEventRepository,
        NetworkUsageEventRepository networkUsageEventRepository,
        InternetUsageEventRepository internetUsageEventRepository,
        DetectionEngine detectionEngine
    ) {
        this.loginEventRepository = loginEventRepository;
        this.logoutEventRepository = logoutEventRepository;
        this.runningAppSnapshotRepository = runningAppSnapshotRepository;
        this.usbEventRepository = usbEventRepository;
        this.vpnEventRepository = vpnEventRepository;
        this.idleEventRepository = idleEventRepository;
        this.networkUsageEventRepository = networkUsageEventRepository;
        this.internetUsageEventRepository = internetUsageEventRepository;
        this.detectionEngine = detectionEngine;
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

    @Transactional
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

        usbEventRepository.saveAndFlush(event);

        DetectionContext context = new DetectionContext(
            "USB_EVENT",
            device.getId(),
            null,
            event.getEventTime(),
            event
        );
        detectionEngine.evaluate(context);

        return MonitoringIngestResponse.ok("USB event recorded.");
    }

    @Transactional
    public MonitoringIngestResponse recordVpn(EndpointDevice device, VpnEventRequest request) {
        VpnEvent event = new VpnEvent();
        event.setEndpoint(device);
        event.setAdapterName(request.adapterName());
        event.setActive(request.active());
        vpnEventRepository.save(event);
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
        internetUsageEventRepository.save(event);
        return MonitoringIngestResponse.ok("Internet usage recorded.");
    }

    // -----------------------------------------------------------------
    // Reads (Phase 4B) - each: unfiltered page when endpointId is null,
    // filtered to one endpoint's rows otherwise. Newest first always.
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<LoginEventResponse> listLoginEvents(UUID endpointId, Pageable pageable) {
        Page<LoginEvent> page = endpointId == null
            ? loginEventRepository.findAllByOrderByLoginTimeDesc(pageable)
            : loginEventRepository.findByEndpoint_IdOrderByLoginTimeDesc(endpointId, pageable);
        return PageResponse.of(page.map(e -> new LoginEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getOsUsername(), e.getSessionId(), e.getLoginTime(), e.getReceivedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<LogoutEventResponse> listLogoutEvents(UUID endpointId, Pageable pageable) {
        Page<LogoutEvent> page = endpointId == null
            ? logoutEventRepository.findAllByOrderByLogoutTimeDesc(pageable)
            : logoutEventRepository.findByEndpoint_IdOrderByLogoutTimeDesc(endpointId, pageable);
        return PageResponse.of(page.map(e -> new LogoutEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getOsUsername(), e.getSessionId(), e.getLogoutTime(), e.getReceivedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<UsbEventResponse> listUsbEvents(UUID endpointId, Pageable pageable) {
        Page<UsbEvent> page = endpointId == null
            ? usbEventRepository.findAllByOrderByEventTimeDesc(pageable)
            : usbEventRepository.findByEndpoint_IdOrderByEventTimeDesc(endpointId, pageable);
        return PageResponse.of(page.map(e -> new UsbEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getDeviceName(), e.getDeviceId(), e.getVendorId(), e.getProductId(),
            e.getAction() != null ? e.getAction().name() : null,
            e.getEventTime(), e.getReceivedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<VpnEventResponse> listVpnEvents(UUID endpointId, Pageable pageable) {
        Page<VpnEvent> page = endpointId == null
            ? vpnEventRepository.findAllByOrderByDetectedAtDesc(pageable)
            : vpnEventRepository.findByEndpoint_IdOrderByDetectedAtDesc(endpointId, pageable);
        return PageResponse.of(page.map(e -> new VpnEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getAdapterName(), e.isActive(), e.getDetectedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<IdleEventResponse> listIdleEvents(UUID endpointId, Pageable pageable) {
        Page<IdleEvent> page = endpointId == null
            ? idleEventRepository.findAllByOrderByRecordedAtDesc(pageable)
            : idleEventRepository.findByEndpoint_IdOrderByRecordedAtDesc(endpointId, pageable);
        return PageResponse.of(page.map(e -> new IdleEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getIdleSeconds(), e.getRecordedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<NetworkUsageEventResponse> listNetworkUsageEvents(UUID endpointId, Pageable pageable) {
        Page<NetworkUsageEvent> page = endpointId == null
            ? networkUsageEventRepository.findAllByOrderByRecordedAtDesc(pageable)
            : networkUsageEventRepository.findByEndpoint_IdOrderByRecordedAtDesc(endpointId, pageable);
        return PageResponse.of(page.map(e -> new NetworkUsageEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getBytesSent(), e.getBytesReceived(), e.getInterfaceName(), e.getRecordedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<InternetUsageEventResponse> listInternetUsageEvents(UUID endpointId, Pageable pageable) {
        Page<InternetUsageEvent> page = endpointId == null
            ? internetUsageEventRepository.findAllByOrderByRecordedAtDesc(pageable)
            : internetUsageEventRepository.findByEndpoint_IdOrderByRecordedAtDesc(endpointId, pageable);
        return PageResponse.of(page.map(e -> new InternetUsageEventResponse(
            e.getId(), e.getEndpoint().getId(), e.getEndpoint().getHostname(),
            e.getUploadMb(), e.getDownloadMb(), e.getPeriodSeconds(), e.getRecordedAt()
        )));
    }

    @Transactional(readOnly = true)
    public PageResponse<RunningAppSnapshotResponse> listRunningAppSnapshots(UUID endpointId, Pageable pageable) {
        Page<RunningAppSnapshot> page = endpointId == null
            ? runningAppSnapshotRepository.findAllByOrderByCapturedAtDesc(pageable)
            : runningAppSnapshotRepository.findByEndpoint_IdOrderByCapturedAtDesc(endpointId, pageable);
        return PageResponse.of(page.map(s -> new RunningAppSnapshotResponse(
            s.getId(), s.getEndpoint().getId(), s.getEndpoint().getHostname(), s.getCapturedAt(),
            s.getApps().stream()
                .map(a -> new RunningAppSnapshotResponse.AppEntry(a.getProcessName(), a.getWindowTitle(), a.getPid()))
                .toList()
        )));
    }
}
