import type {
  EndpointSummaryResponse,
  NetworkUsageEventResponse,
} from "@/types/api";
/**
 * Pure helpers behind the Network Usage chart. No React, no I/O - everything
 * here takes the backend's NetworkUsageEventResponse rows (GET
 * /monitoring/network-usage) and returns plain data, so the page only has to
 * render it.
 *
 * Each row is the number of bytes sent/received by ONE endpoint since that
 * endpoint's previous sample (the agent reports deltas, not running totals,
 * and the response carries no period length) - so values are "per sample",
 * never a per-second rate.
 */

export type ByteUnit = "B" | "KB" | "MB" | "GB";

const UNITS: { unit: ByteUnit; divisor: number }[] = [
  { unit: "B", divisor: 1 },
  { unit: "KB", divisor: 1024 },
  { unit: "MB", divisor: 1024 ** 2 },
  { unit: "GB", divisor: 1024 ** 3 },
];

/** A byte count is only usable if it is a finite, non-negative number - the
 * backend field is a nullable Long and the agent clamps deltas at zero, so
 * anything else is missing/corrupt data rather than a real measurement. */
export function isValidByteCount(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

/** Picks the largest unit in which `bytes` is at least 1 (so 1536 B is
 * "KB", 0 stays "B"). Used for both value labels and the chart's y-axis. */
export function pickUnit(bytes: number): { unit: ByteUnit; divisor: number } {
  let chosen = UNITS[0];
  for (const candidate of UNITS) {
    if (bytes >= candidate.divisor) chosen = candidate;
  }
  return chosen;
}

function trimNumber(value: number): string {
  if (value >= 100) return value.toFixed(0);
  if (value >= 10) return value.toFixed(1);
  return value.toFixed(2);
}

/** "—" for missing/invalid input, otherwise e.g. "0 B", "812 B", "1.50 KB",
 * "12.3 MB", "1.25 GB" (binary units, matching the rest of the app, which
 * already divides by 1024 for MB). */
export function formatBytes(bytes: number | null | undefined): string {
  if (!isValidByteCount(bytes)) return "—";
  if (bytes < UNITS[1].divisor) return `${Math.round(bytes)} B`;
  const { unit, divisor } = pickUnit(bytes);
  return `${trimNumber(bytes / divisor)} ${unit}`;
}

/** Axis tick label for a value already expressed in the chart's unit. */
export function formatAxisValue(value: number): string {
  if (!Number.isFinite(value)) return "";
  return value >= 100 ? value.toFixed(0) : Number(value.toFixed(2)).toString();
}

export interface NetworkUsagePoint {
  /** Epoch millis of the sample - sampledAt (agent collection time) when the
   * backend has it, otherwise recordedAt (backend ingestion time). */
  ts: number;
  timeSource: "sampled" | "recorded";
  sent: number;
  received: number;
  total: number;
}

export interface NetworkUsageSeries {
  /** Chronological (oldest first), only rows with a valid time and values. */
  points: NetworkUsagePoint[];
  /** Rows dropped because a timestamp or byte count was missing/invalid. */
  skipped: number;
}

function parseTime(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const ms = Date.parse(iso);
  return Number.isNaN(ms) ? null : ms;
}

/**
 * Turns a page of backend rows into a clean chronological series.
 *  - Sorted by the sample's own time, NOT by the order received: the backend
 *    pages by ingestion time (newest first), but a sample replayed from the
 *    agent's offline queue arrives late with an old sampledAt.
 *  - Rows with a missing/invalid byte count or timestamp are excluded and
 *    counted in `skipped` - never silently treated as zero, which would draw
 *    a fake "no traffic" point.
 */
export function buildNetworkUsageSeries(
  events: readonly NetworkUsageEventResponse[],
): NetworkUsageSeries {
  const points: NetworkUsagePoint[] = [];
  let skipped = 0;

  for (const event of events) {
    const sampled = parseTime(event.sampledAt);
    const ts = sampled ?? parseTime(event.recordedAt);
    if (
      ts === null ||
      !isValidByteCount(event.bytesSent) ||
      !isValidByteCount(event.bytesReceived)
    ) {
      skipped += 1;
      continue;
    }
    points.push({
      ts,
      timeSource: sampled !== null ? "sampled" : "recorded",
      sent: event.bytesSent,
      received: event.bytesReceived,
      total: event.bytesSent + event.bytesReceived,
    });
  }

  points.sort((a, b) => a.ts - b.ts);
  return { points, skipped };
}

export interface NetworkUsageSummary {
  latest: NetworkUsagePoint;
  windowTotalBytes: number;
  peak: NetworkUsagePoint;
  /** Median gap between consecutive samples, in seconds (null with < 2 points). */
  medianIntervalSeconds: number | null;
  spanMs: number;
}

/** Statistics over exactly the points being charted (a bounded recent
 * window - not a 24h aggregate). Returns null for an empty series. */
export function summarizeNetworkUsage(
  points: readonly NetworkUsagePoint[],
): NetworkUsageSummary | null {
  if (points.length === 0) return null;

  let windowTotalBytes = 0;
  let peak = points[0];
  for (const point of points) {
    windowTotalBytes += point.total;
    if (point.total > peak.total) peak = point;
  }

  const gaps: number[] = [];
  for (let i = 1; i < points.length; i += 1) {
    gaps.push((points[i].ts - points[i - 1].ts) / 1000);
  }
  gaps.sort((a, b) => a - b);
  const mid = Math.floor(gaps.length / 2);
  const medianIntervalSeconds =
    gaps.length === 0 ? null : gaps.length % 2 === 1 ? gaps[mid] : (gaps[mid - 1] + gaps[mid]) / 2;

  return {
    latest: points[points.length - 1],
    windowTotalBytes,
    peak,
    medianIntervalSeconds,
    spanMs: points[points.length - 1].ts - points[0].ts,
  };
}

/** "58m", "1h 05m", "42s" - a compact duration for the window span. */
export function formatDuration(ms: number): string {
  if (!Number.isFinite(ms) || ms < 0) return "—";
  const totalSeconds = Math.round(ms / 1000);
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const totalMinutes = Math.floor(totalSeconds / 60);
  if (totalMinutes < 60) return `${totalMinutes}m`;
  const hours = Math.floor(totalMinutes / 60);
  return `${hours}h ${String(totalMinutes % 60).padStart(2, "0")}m`;
}
export interface FleetNetworkUsageRow {
  endpointId: string;
  hostname: string;
  status: string;
  upload: number;
  download: number;
  total: number;
  lastSampleAt: string | null;
}

export interface FleetNetworkUsagePoint {
  ts: number;
  upload: number;
  download: number;
  total: number;
}

function parseFleetTimestamp(
  sampledAt: string | null | undefined,
  recordedAt: string | null | undefined,
): number | null {
  const value = sampledAt ?? recordedAt;

  if (!value) return null;

  const timestamp = Date.parse(value);

  return Number.isFinite(timestamp) ? timestamp : null;
}

/**
 * Aggregates recent network-usage samples by endpoint.
 *
 * IMPORTANT:
 * The backend records are deltas since each endpoint's previous sample.
 * Therefore these values represent the sum of the fetched recent records,
 * not a lifetime total and not a fixed time duration.
 */
export function buildFleetNetworkUsageRows(
  events: readonly NetworkUsageEventResponse[],
  endpoints: readonly EndpointSummaryResponse[],
): FleetNetworkUsageRow[] {
  const endpointMap = new Map(
    endpoints.map((endpoint) => [endpoint.id, endpoint]),
  );

  const usageMap = new Map<
    string,
    {
      upload: number;
      download: number;
      lastSampleAt: string | null;
    }
  >();

  for (const event of events) {
    if (
      !isValidByteCount(event.bytesSent) ||
      !isValidByteCount(event.bytesReceived)
    ) {
      continue;
    }

    const timestamp = parseFleetTimestamp(
      event.sampledAt,
      event.recordedAt,
    );

    const existing = usageMap.get(event.endpointId);

    if (!existing) {
      usageMap.set(event.endpointId, {
        upload: event.bytesSent,
        download: event.bytesReceived,
        lastSampleAt:
          timestamp !== null
            ? event.sampledAt ?? event.recordedAt
            : null,
      });

      continue;
    }

    existing.upload += event.bytesSent;
    existing.download += event.bytesReceived;

    if (timestamp !== null) {
      const existingTimestamp = existing.lastSampleAt
        ? Date.parse(existing.lastSampleAt)
        : null;

      if (
        existingTimestamp === null ||
        timestamp > existingTimestamp
      ) {
        existing.lastSampleAt =
          event.sampledAt ?? event.recordedAt;
      }
    }
  }

  return Array.from(usageMap.entries())
    .map(([endpointId, usage]) => {
      const endpoint = endpointMap.get(endpointId);

      return {
        endpointId,
        hostname: endpoint?.hostname ?? "Unknown endpoint",
        status: endpoint?.status ?? "UNKNOWN",
        upload: usage.upload,
        download: usage.download,
        total: usage.upload + usage.download,
        lastSampleAt: usage.lastSampleAt,
      };
    })
    .sort((a, b) => b.total - a.total);
}

/**
 * Builds one aggregate fleet series from the fetched recent records.
 *
 * Samples are grouped by timestamp. This is intentionally an aggregate
 * graph rather than one graph per endpoint.
 */
export function buildFleetNetworkUsageSeries(
  events: readonly NetworkUsageEventResponse[],
): FleetNetworkUsagePoint[] {
  const buckets = new Map<
    number,
    {
      upload: number;
      download: number;
    }
  >();

  for (const event of events) {
    if (
      !isValidByteCount(event.bytesSent) ||
      !isValidByteCount(event.bytesReceived)
    ) {
      continue;
    }

    const ts = parseFleetTimestamp(
      event.sampledAt,
      event.recordedAt,
    );

    if (ts === null) continue;

    const existing = buckets.get(ts);

    if (existing) {
      existing.upload += event.bytesSent;
      existing.download += event.bytesReceived;
    } else {
      buckets.set(ts, {
        upload: event.bytesSent,
        download: event.bytesReceived,
      });
    }
  }

  return Array.from(buckets.entries())
    .map(([ts, values]) => ({
      ts,
      upload: values.upload,
      download: values.download,
      total: values.upload + values.download,
    }))
    .sort((a, b) => a.ts - b.ts);
}
