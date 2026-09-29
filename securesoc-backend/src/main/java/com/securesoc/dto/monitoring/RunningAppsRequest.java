package com.securesoc.dto.monitoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Mirrors agent.py's run_monitoring_cycle() running-apps POST body:
 * {"applications": [{"processName": ..., "windowTitle": ..., "pid": ..., "commandLine": ...}, ...]}
 * - the exact list shape collector.get_running_applications() returns.
 * commandLine is nullable for backward compatibility with older agents
 * that do not send it yet (see collector.py's get_running_applications). */
public record RunningAppsRequest(
    @NotEmpty @Valid List<AppEntry> applications
) {
    public record AppEntry(
        String processName,
        String windowTitle,
        Integer pid,
        String commandLine
    ) {}
}
