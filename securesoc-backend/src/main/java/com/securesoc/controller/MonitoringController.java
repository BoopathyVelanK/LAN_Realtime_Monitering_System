package com.securesoc.controller;

import com.securesoc.dto.monitoring.*;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.service.MonitoringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints consumed exclusively by securesoc-agent/agent.py's
 * run_monitoring_cycle(), send_login_event(), and send_logout_event() -
 * see collector.py for the exact payload shapes each of these mirrors.
 *
 * All paths here are covered by AgentTokenAuthFilter (matched via its
 * shouldNotFilter's "/monitoring/" prefix check) and SecurityConfig's
 * anyRequest().authenticated() - a request without a valid X-Agent-Token
 * never reaches these methods, exactly like AgentController.heartbeat.
 *
 * Pure ingestion, no detection/risk logic - see MonitoringService's
 * class Javadoc.
 */
@RestController
@RequestMapping("/monitoring")
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

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
}
