import type { EndpointStatus } from "@/types/api";

/**
 * Maps the backend's EndpointDevice.status string (currently ONLINE |
 * OFFLINE - see EndpointSummaryResponse.status) onto the StatusDot
 * component's display variants. StatusDot also supports "idle" and
 * "exam", but the backend has no concept of either yet (no idle-vs-active
 * distinction on EndpointDevice, no exam-mode field) - Phase 3/7 work.
 * Anything other than a recognized ONLINE falls back to "offline" rather
 * than guessing, since an unrecognized status should never be displayed
 * as if the endpoint were healthy.
 */
export function mapEndpointStatus(status: EndpointStatus): "online" | "offline" | "idle" | "exam" {
  return status === "ONLINE" ? "online" : "offline";
}

/**
 * Formats an ISO-8601 timestamp (e.g. EndpointSummaryResponse.lastHeartbeatAt)
 * as a short relative-time string ("just now", "5m ago", "2h 14m ago").
 * Returns "never" for null/missing timestamps (an endpoint that has
 * registered but never sent a heartbeat).
 */
export function formatRelativeTime(iso: string | null): string {
  if (!iso) return "never";
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "never";

  const diffMs = Date.now() - then;
  if (diffMs < 0) return "just now";

  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;

  const hours = Math.floor(minutes / 60);
  const remMinutes = minutes % 60;
  if (hours < 24) return `${hours}h ${remMinutes}m ago`;

  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}
