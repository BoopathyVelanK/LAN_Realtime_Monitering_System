package com.securesoc.dto.monitoring;

/** Uniform ack shape returned by every /monitoring/** endpoint. The agent
 * (agent.py's _post_monitoring) only checks the HTTP status code - a 2xx
 * means "handled" and clears the offline queue entry if this came from a
 * replay - so the body content isn't load-bearing for the agent, but is
 * useful for manual curl testing and future frontend/audit use. */
public record MonitoringIngestResponse(
    String status,
    String message
) {
    public static MonitoringIngestResponse ok(String message) {
        return new MonitoringIngestResponse("OK", message);
    }
}
