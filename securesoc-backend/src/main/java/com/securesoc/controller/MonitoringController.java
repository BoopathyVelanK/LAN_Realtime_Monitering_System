package com.securesoc.controller;

import com.securesoc.dto.PageResponse;
import com.securesoc.dto.monitoring.*;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.service.MonitoringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Two audiences share this controller:
 *  - POST /monitoring/** - consumed exclusively by securesoc-agent/
 *    agent.py's run_monitoring_cycle(), send_login_event(), and
 *    send_logout_event() (see collector.py for the exact payload shapes).
 *    Authenticated via AgentTokenAuthFilter (X-Agent-Token header).
 *  - GET /monitoring/** (Phase 4B) - consumed by the frontend dashboard
 *    to read back what the agent has already ingested. Authenticated via
 *    the normal JWT flow (JwtAuthenticationFilter) - AgentTokenAuthFilter
 *    is a no-op on these requests since browsers never send
 *    X-Agent-Token, and SecurityConfig's anyRequest().authenticated()
 *    accepts either authentication source with no role restriction, so
 *    no security changes were needed to add these.
 *
 * Pure ingestion/reads, no detection/risk logic - see MonitoringService's
 * class Javadoc.
 */
@RestController
@RequestMapping("/monitoring")
public class MonitoringController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    // -----------------------------------------------------------------
    // Ingest (Phase 3 - unchanged)
    // -----------------------------------------------------------------

    @PostMapping("/login")
    public ResponseEntity<MonitoringIngestResponse> login(
        @Valid @RequestBody LoginEventRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(monitoringService.recordLogin(device(httpRequest), request));
    }

    @PostMapping("/logout")
    public ResponseEntity<MonitoringIngestResponse> logout(
        @Valid @RequestBody LogoutEventRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(monitoringService.recordLogout(device(httpRequest), request));
    }

    @PostMapping("/running-apps")
    public ResponseEntity<MonitoringIngestResponse> runningApps(
        @Valid @RequestBody RunningAppsRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(monitoringService.recordRunningApps(device(httpRequest), request));
    }

    @PostMapping("/usb")
    public ResponseEntity<MonitoringIngestResponse> usb(
        @RequestBody UsbEventRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(monitoringService.recordUsb(device(httpRequest), request));
    }

    @PostMapping("/vpn")
    public ResponseEntity<MonitoringIngestResponse> vpn(
        @RequestBody VpnEventRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(monitoringService.recordVpn(device(httpRequest), request));
    }

    @PostMapping("/idle")
    public ResponseEntity<MonitoringIngestResponse> idle(
        @Valid @RequestBody IdleEventRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(monitoringService.recordIdle(device(httpRequest), request));
    }

    @PostMapping("/network-usage")
    public ResponseEntity<MonitoringIngestResponse> networkUsage(
        @RequestBody NetworkUsageEventRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(monitoringService.recordNetworkUsage(device(httpRequest), request));
    }

    @PostMapping("/internet-usage")
    public ResponseEntity<MonitoringIngestResponse> internetUsage(
        @RequestBody InternetUsageEventRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(monitoringService.recordInternetUsage(device(httpRequest), request));
    }

    /** AgentTokenAuthFilter has already resolved + authenticated the
     * device and stashed it as a request attribute before any of these
     * methods run - identical pattern to AgentController.heartbeat. */
    private EndpointDevice device(HttpServletRequest httpRequest) {
        return (EndpointDevice) httpRequest.getAttribute("endpointDevice");
    }

    // -----------------------------------------------------------------
    // Reads (Phase 4B) - ?endpointId=<uuid> filters to one endpoint;
    // omit it for the fleet-wide feed. ?page / ?size are 0-indexed /
    // capped at MAX_PAGE_SIZE. Always newest first - see repository
    // Javadoc on why sort isn't a client-supplied parameter.
    // -----------------------------------------------------------------

    @GetMapping("/login")
    public ResponseEntity<PageResponse<LoginEventResponse>> listLoginEvents(
        @RequestParam(required = false) UUID endpointId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(monitoringService.listLoginEvents(endpointId, pageable(page, size)));
    }

    @GetMapping("/logout")
    public ResponseEntity<PageResponse<LogoutEventResponse>> listLogoutEvents(
        @RequestParam(required = false) UUID endpointId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(monitoringService.listLogoutEvents(endpointId, pageable(page, size)));
    }

    @GetMapping("/usb")
    public ResponseEntity<PageResponse<UsbEventResponse>> listUsbEvents(
        @RequestParam(required = false) UUID endpointId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(monitoringService.listUsbEvents(endpointId, pageable(page, size)));
    }

    @GetMapping("/vpn")
    public ResponseEntity<PageResponse<VpnEventResponse>> listVpnEvents(
        @RequestParam(required = false) UUID endpointId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(monitoringService.listVpnEvents(endpointId, pageable(page, size)));
    }

    @GetMapping("/idle")
    public ResponseEntity<PageResponse<IdleEventResponse>> listIdleEvents(
        @RequestParam(required = false) UUID endpointId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(monitoringService.listIdleEvents(endpointId, pageable(page, size)));
    }

    @GetMapping("/network-usage")
    public ResponseEntity<PageResponse<NetworkUsageEventResponse>> listNetworkUsageEvents(
        @RequestParam(required = false) UUID endpointId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(monitoringService.listNetworkUsageEvents(endpointId, pageable(page, size)));
    }

    @GetMapping("/internet-usage")
    public ResponseEntity<PageResponse<InternetUsageEventResponse>> listInternetUsageEvents(
        @RequestParam(required = false) UUID endpointId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(monitoringService.listInternetUsageEvents(endpointId, pageable(page, size)));
    }

    @GetMapping("/running-apps")
    public ResponseEntity<PageResponse<RunningAppSnapshotResponse>> listRunningAppSnapshots(
        @RequestParam(required = false) UUID endpointId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(monitoringService.listRunningAppSnapshots(endpointId, pageable(page, size)));
    }

    /** Shared by every list* endpoint above - clamps page size so a
     * mistaken/malicious ?size=999999 can't force one huge query. */
    private Pageable pageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
