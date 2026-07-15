package com.securesoc.service;

import com.securesoc.dto.monitoring.*;
import com.securesoc.entity.*;
import com.securesoc.entity.UsbEvent.Action;
import com.securesoc.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Handles ingestion for every /monitoring/** endpoint the agent posts to
 * (see agent.py's run_monitoring_cycle, send_login_event,
 * send_logout_event, and _post_monitoring - which also drives the
 * offline-queue replay path in offline_queue.py). Pure ingestion only:
 * this service stores events as-is and makes no policy/risk decisions -
 * that's Phase 4 (detection engine).
 *
 * Every method takes the already-authenticated EndpointDevice (resolved
 * by AgentTokenAuthFilter, same pattern as AgentService.heartbeat) rather
 * than re-resolving it from a token.
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

    public MonitoringService(
        LoginEventRepository loginEventRepository,
        LogoutEventRepository logoutEventRepository,
        RunningAppSnapshotRepository runningAppSnapshotRepository,
        UsbEventRepository usbEventRepository,
        VpnEventRepository vpnEventRepository,
        IdleEventRepository idleEventRepository,
        NetworkUsageEventRepository networkUsageEventRepository,
        InternetUsageEventRepository internetUsageEventRepository
    ) {
        this.loginEventRepository = loginEventRepository;
        this.logoutEventRepository = logoutEventRepository;
        this.runningAppSnapshotRepository = runningAppSnapshotRepository;
        this.usbEventRepository = usbEventRepository;
        this.vpnEventRepository = vpnEventRepository;
        this.idleEventRepository = idleEventRepository;
        this.networkUsageEventRepository = networkUsageEventRepository;
        this.internetUsageEventRepository = internetUsageEventRepository;
    }

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

        usbEventRepository.save(event);
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
}
