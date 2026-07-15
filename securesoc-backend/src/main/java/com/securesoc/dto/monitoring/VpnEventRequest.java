package com.securesoc.dto.monitoring;

/** Mirrors agent.py's run_monitoring_cycle() VPN POST body:
 * {"adapterName": <name or null>, "active": true|false} - one call per
 * adapter found by collector.detect_vpn_adapters(), or a single
 * active=false call with adapterName=null when none are found. */
public record VpnEventRequest(
    String adapterName,
    boolean active
) {}
