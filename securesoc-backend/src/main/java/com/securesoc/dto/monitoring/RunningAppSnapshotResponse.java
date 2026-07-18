package com.securesoc.dto.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read shape for GET /monitoring/running-apps - mirrors
 * RunningAppSnapshot plus the endpoint's hostname, with its RunningApp
 * rows flattened into a list, mirroring RunningAppsRequest's own
 * nested-record shape from the ingest side. */
public record RunningAppSnapshotResponse(
    UUID id,
    UUID endpointId,
    String hostname,
    Instant capturedAt,
    List<AppEntry> apps
) {
    public record AppEntry(
        String processName,
        String windowTitle,
        Integer pid
    ) {}
}
