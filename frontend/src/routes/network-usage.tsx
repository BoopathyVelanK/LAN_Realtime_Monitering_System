import { useMemo, useState, type ReactNode } from "react";

import { createFileRoute } from "@tanstack/react-router";
import { isAxiosError } from "axios";

import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { AppShell, SectionCard, StatCard } from "@/components/AppShell";
import { useEndpoints, useNetworkUsageEvents } from "@/api/queries";
import { USE_MOCKS } from "@/config";

import {
  buildFleetNetworkUsageRows,
  buildFleetNetworkUsageSeries,
  buildNetworkUsageSeries,
  formatAxisValue,
  formatBytes,
  formatDuration,
  pickUnit,
  summarizeNetworkUsage,
  type FleetNetworkUsagePoint,
  type FleetNetworkUsageRow,
  type NetworkUsagePoint,
} from "@/lib/networkUsage";

export const Route = createFileRoute("/network-usage")({
  head: () => ({
    meta: [{ title: "SecureSOC — Network Usage" }],
  }),
  component: NetworkUsage,
});

/**
 * Most recent samples fetched for the selected endpoint.
 *
 * The backend returns newest records first and caps a page at 200.
 * We intentionally use only 30 records for the detailed endpoint chart
 * so old outliers cannot visually compress the recent trend.
 */
const SAMPLE_WINDOW = 30;

/**
 * Fleet query is deliberately larger than the endpoint detail query.
 *
 * IMPORTANT:
 * This is a RECORD COUNT, not a time duration.
 * Do not describe it as "last 15 minutes", "last hour", etc.
 */
const FLEET_SAMPLE_WINDOW = 200;

type ChartDatum = {
  ts: number;
  sent: number;
  received: number;
  point: NetworkUsagePoint;
};

type FleetChartDatum = {
  ts: number;
  upload: number;
  download: number;
  total: number;
};

type FleetSortKey = "total" | "upload" | "download" | "hostname";

const timeFormat: Intl.DateTimeFormatOptions = {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
};

function formatClock(ts: number): string {
  return new Date(ts).toLocaleTimeString([], timeFormat);
}

function formatDateTime(value: string | null): string {
  if (!value) return "—";

  const timestamp = Date.parse(value);

  if (!Number.isFinite(timestamp)) {
    return "—";
  }

  return new Date(timestamp).toLocaleString();
}

function describeError(error: unknown): string {
  if (isAxiosError(error)) {
    const status = error.response?.status;

    if (status === 403) {
      return "Your account is not allowed to view this endpoint's telemetry (HTTP 403).";
    }

    if (status) {
      return `The backend returned HTTP ${status}.`;
    }

    return "Could not reach the backend.";
  }

  return "Unexpected error while loading network telemetry.";
}

function StateMessage({
  tone = "muted",
  children,
}: {
  tone?: "muted" | "error";
  children: ReactNode;
}) {
  return (
    <div
      className={`h-72 flex flex-col items-center justify-center gap-3 text-xs text-center px-6 ${
        tone === "error" ? "text-critical" : "text-muted-foreground"
      }`}
    >
      {children}
    </div>
  );
}

function RetryButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="border border-border rounded px-3 py-1.5 text-[11px] font-bold tracking-wider text-foreground hover:bg-muted"
    >
      RETRY
    </button>
  );
}

function UsageTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: { payload: ChartDatum }[];
}) {
  const point = active ? payload?.[0]?.payload.point : undefined;

  if (!point) return null;

  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-sm">
      <div className="font-bold">{new Date(point.ts).toLocaleString()}</div>

      {point.timeSource === "recorded" && (
        <div className="text-[10px] text-muted-foreground">
          backend ingest time (no agent timestamp)
        </div>
      )}

      <div className="mt-1.5 space-y-0.5">
        <div className="flex justify-between gap-6">
          <span className="text-primary">Upload</span>
          <span>{formatBytes(point.sent)}</span>
        </div>

        <div className="flex justify-between gap-6">
          <span className="text-critical">Download</span>
          <span>{formatBytes(point.received)}</span>
        </div>

        <div className="flex justify-between gap-6 border-t border-border pt-0.5 font-bold">
          <span>Total</span>
          <span>{formatBytes(point.total)}</span>
        </div>
      </div>
    </div>
  );
}

function FleetUsageTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: { payload: FleetChartDatum }[];
}) {
  const point = active ? payload?.[0]?.payload : undefined;

  if (!point) return null;

  return (
    <div className="rounded-md border border-border bg-card px-3 py-2 text-xs shadow-sm">
      <div className="font-bold">{new Date(point.ts).toLocaleString()}</div>

      <div className="mt-1.5 space-y-0.5">
        <div className="flex justify-between gap-6">
          <span className="text-primary">Upload</span>
          <span>{formatBytes(point.upload)}</span>
        </div>

        <div className="flex justify-between gap-6">
          <span className="text-critical">Download</span>
          <span>{formatBytes(point.download)}</span>
        </div>

        <div className="flex justify-between gap-6 border-t border-border pt-0.5 font-bold">
          <span>Total</span>
          <span>{formatBytes(point.total)}</span>
        </div>
      </div>
    </div>
  );
}

function statusClass(status: string): string {
  if (status === "ONLINE") {
    return "text-success";
  }

  if (status === "OFFLINE") {
    return "text-muted-foreground";
  }

  return "text-warning";
}

function NetworkUsage() {
  const endpointsQuery = useEndpoints();

  const endpoints = useMemo(
    () => endpointsQuery.data ?? [],
    [endpointsQuery.data],
  );

  /*
   * -------------------------------------------------------------------------
   * Fleet data
   * -------------------------------------------------------------------------
   */

  const fleetQuery = useNetworkUsageEvents(
    {
      size: FLEET_SAMPLE_WINDOW,
    },
    {
      enabled: endpoints.length > 0,
    },
  );

  const fleetRows = useMemo(
    () =>
      buildFleetNetworkUsageRows(
        fleetQuery.data?.content ?? [],
        endpoints,
      ),
    [fleetQuery.data?.content, endpoints],
  );

  const fleetSeries = useMemo(
    () =>
      buildFleetNetworkUsageSeries(
        fleetQuery.data?.content ?? [],
      ),
    [fleetQuery.data?.content],
  );

  /*
   * Include endpoints that have no records in the current 200-record page.
   *
   * This means the fleet table really represents the endpoint list instead
   * of silently hiding endpoints whose latest usage record is outside the
   * fetched page.
   */
  const fleetDisplayRows = useMemo(() => {
    const usageByEndpoint = new Map(
      fleetRows.map((row) => [row.endpointId, row]),
    );

    return endpoints.map((endpoint) => {
      const usage = usageByEndpoint.get(endpoint.id);

      if (usage) {
        return usage;
      }

      return {
        endpointId: endpoint.id,
        hostname: endpoint.hostname,
        status: endpoint.status,
        upload: 0,
        download: 0,
        total: 0,
        lastSampleAt: null,
      };
    });
  }, [endpoints, fleetRows]);

  const [fleetSortKey, setFleetSortKey] =
    useState<FleetSortKey>("total");

  const sortedFleetRows = useMemo(() => {
    const rows = [...fleetDisplayRows];

    rows.sort((a, b) => {
      switch (fleetSortKey) {
        case "upload":
          return b.upload - a.upload;

        case "download":
          return b.download - a.download;

        case "hostname":
          return a.hostname.localeCompare(b.hostname);

        case "total":
        default:
          return b.total - a.total;
      }
    });

    return rows;
  }, [fleetDisplayRows, fleetSortKey]);

  const fleetTotals = useMemo(
    () =>
      fleetRows.reduce(
        (totals, row) => ({
          upload: totals.upload + row.upload,
          download: totals.download + row.download,
          total: totals.total + row.total,
        }),
        {
          upload: 0,
          download: 0,
          total: 0,
        },
      ),
    [fleetRows],
  );

  const fleetUnit = useMemo(
    () =>
      pickUnit(
        fleetSeries.reduce(
          (max, point) => Math.max(max, point.total),
          0,
        ),
      ),
    [fleetSeries],
  );

  const fleetChartData: FleetChartDatum[] = useMemo(
    () =>
      fleetSeries.map((point: FleetNetworkUsagePoint) => ({
        ts: point.ts,
        upload: point.upload / fleetUnit.divisor,
        download: point.download / fleetUnit.divisor,
        total: point.total / fleetUnit.divisor,
      })),
    [fleetSeries, fleetUnit],
  );

  /*
   * -------------------------------------------------------------------------
   * Selected endpoint
   * -------------------------------------------------------------------------
   */

  const [selectedId, setSelectedId] = useState<string | null>(null);

  const defaultId =
    (
      endpoints.find((endpoint) => endpoint.status === "ONLINE") ??
      endpoints[0]
    )?.id ?? null;

  const activeId =
    selectedId && endpoints.some((endpoint) => endpoint.id === selectedId)
      ? selectedId
      : defaultId;

  const activeEndpoint = endpoints.find(
    (endpoint) => endpoint.id === activeId,
  );

  const usageQuery = useNetworkUsageEvents(
    {
      endpointId: activeId ?? undefined,
      size: SAMPLE_WINDOW,
    },
    {
      enabled: activeId !== null,
    },
  );

  const rows = usageQuery.data?.content;

  const series = useMemo(
    () => buildNetworkUsageSeries(rows ?? []),
    [rows],
  );

  const summary = useMemo(
    () => summarizeNetworkUsage(series.points),
    [series.points],
  );

  const unit = useMemo(
    () =>
      pickUnit(
        series.points.reduce(
          (max, point) => Math.max(max, point.total),
          0,
        ),
      ),
    [series.points],
  );

  const chartData: ChartDatum[] = useMemo(
    () =>
      series.points.map((point) => ({
        ts: point.ts,
        sent: point.sent / unit.divisor,
        received: point.received / unit.divisor,
        point,
      })),
    [series.points, unit],
  );

  const refreshFailed =
    usageQuery.isError && rows !== undefined;

  /*
   * -------------------------------------------------------------------------
   * Endpoint detail state
   * -------------------------------------------------------------------------
   */

  let body: ReactNode;

  if (endpointsQuery.isLoading) {
    body = <StateMessage>Loading endpoints…</StateMessage>;
  } else if (endpointsQuery.isError) {
    body = (
      <StateMessage tone="error">
        <span>
          Could not load the endpoint list.{" "}
          {describeError(endpointsQuery.error)}
        </span>

        <RetryButton
          onClick={() => void endpointsQuery.refetch()}
        />
      </StateMessage>
    );
  } else if (activeId === null) {
    body = (
      <StateMessage>
        No endpoints to show. Endpoints appear here once an agent has
        registered (and, for Faculty accounts, only those in your assigned
        labs).
      </StateMessage>
    );
  } else if (usageQuery.isPending) {
    body = <StateMessage>Loading network telemetry…</StateMessage>;
  } else if (
    usageQuery.isError &&
    rows === undefined
  ) {
    body = (
      <StateMessage tone="error">
        <span>
          Could not load network telemetry.{" "}
          {describeError(usageQuery.error)}
        </span>

        <RetryButton
          onClick={() => void usageQuery.refetch()}
        />
      </StateMessage>
    );
  } else if (
    rows === undefined ||
    rows.length === 0
  ) {
    body = (
      <StateMessage>
        No network telemetry has been recorded for this endpoint yet.
        Samples appear about one monitoring interval after its agent
        starts (the agent&apos;s first reading is only a baseline).
      </StateMessage>
    );
  } else if (series.points.length === 0) {
    body = (
      <StateMessage tone="error">
        The backend returned {rows.length} sample
        {rows.length === 1 ? "" : "s"}, but none had a valid timestamp
        and byte counts, so there is nothing to chart.
      </StateMessage>
    );
  } else {
    body = (
      <>
        <div className="h-72">
          <ResponsiveContainer
            width="100%"
            height="100%"
          >
            <AreaChart
              data={chartData}
              margin={{
                top: 8,
                right: 16,
                left: 8,
                bottom: 0,
              }}
            >
              <defs>
                <linearGradient
                  id="net-up"
                  x1="0"
                  y1="0"
                  x2="0"
                  y2="1"
                >
                  <stop
                    offset="0%"
                    stopColor="var(--primary)"
                    stopOpacity={0.5}
                  />

                  <stop
                    offset="100%"
                    stopColor="var(--primary)"
                    stopOpacity={0}
                  />
                </linearGradient>

                <linearGradient
                  id="net-down"
                  x1="0"
                  y1="0"
                  x2="0"
                  y2="1"
                >
                  <stop
                    offset="0%"
                    stopColor="var(--critical)"
                    stopOpacity={0.4}
                  />

                  <stop
                    offset="100%"
                    stopColor="var(--critical)"
                    stopOpacity={0}
                  />
                </linearGradient>
              </defs>

              <CartesianGrid
                stroke="var(--border)"
                strokeDasharray="2 4"
              />

              <XAxis
                dataKey="ts"
                type="number"
                scale="time"
                domain={["dataMin", "dataMax"]}
                tickFormatter={formatClock}
                stroke="var(--muted-foreground)"
                fontSize={10}
              />

              <YAxis
                stroke="var(--muted-foreground)"
                fontSize={10}
                domain={[0, "auto"]}
                tickFormatter={formatAxisValue}
                label={{
                  value: `${unit.unit} / sample`,
                  angle: -90,
                  position: "insideLeft",
                  fontSize: 10,
                  fill: "var(--muted-foreground)",
                }}
              />

              <Tooltip content={<UsageTooltip />} />

              <Area
                type="monotone"
                dataKey="received"
                stackId="usage"
                stroke="var(--critical)"
                fill="url(#net-down)"
                strokeWidth={2}
                dot={chartData.length < 3}
                isAnimationActive={false}
              />

              <Area
                type="monotone"
                dataKey="sent"
                stackId="usage"
                stroke="var(--primary)"
                fill="url(#net-up)"
                strokeWidth={2}
                dot={chartData.length < 3}
                isAnimationActive={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1 text-[10px] tracking-wider text-muted-foreground">
          <span className="inline-flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-primary" />
            UPLOAD
          </span>

          <span className="inline-flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-critical" />
            DOWNLOAD
          </span>

          <span>STACKED HEIGHT = TOTAL</span>

          <span>
            BYTES SINCE THE PREVIOUS SAMPLE, NOT A PER-SECOND RATE
          </span>

          {summary?.medianIntervalSeconds != null && (
            <span>
              SAMPLE INTERVAL ≈{" "}
              {Math.round(summary.medianIntervalSeconds)}s
            </span>
          )}
        </div>

        {chartData.length === 1 && (
          <div className="mt-2 text-[11px] text-muted-foreground">
            Only one sample so far — a trend needs at least two.
          </div>
        )}

        {series.skipped > 0 && (
          <div className="mt-2 text-[11px] text-critical">
            {series.skipped} of {rows.length} samples were skipped
            (missing or invalid timestamp or byte count).
          </div>
        )}

        {refreshFailed && (
          <div className="mt-2 text-[11px] text-critical">
            The latest refresh failed (
            {describeError(usageQuery.error)}); showing the last
            data received.
          </div>
        )}
      </>
    );
  }

  const endpointPicker = (
    <label className="flex items-center gap-2 text-[10px] tracking-widest text-muted-foreground font-bold">
      ENDPOINT

      <select
        value={activeId ?? ""}
        onChange={(event) =>
          setSelectedId(event.target.value)
        }
        disabled={endpoints.length === 0}
        className="bg-card border border-border rounded-md px-3 py-1.5 text-xs font-normal tracking-normal text-foreground max-w-xs"
      >
        {endpoints.length === 0 && (
          <option value="">No endpoints</option>
        )}

        {endpoints.map((endpoint) => (
          <option
            key={endpoint.id}
            value={endpoint.id}
          >
            {endpoint.hostname} ·{" "}
            {endpoint.ipAddress || "no IP"} ·{" "}
            {endpoint.status}
          </option>
        ))}
      </select>
    </label>
  );

  const totalRecordedSamples =
    usageQuery.data?.totalElements;

  /*
   * -------------------------------------------------------------------------
   * Render
   * -------------------------------------------------------------------------
   */

  return (
    <AppShell
      title="Network Usage"
      subtitle="BANDWIDTH TELEMETRY"
    >
      <div className="px-8 pb-8">
        {USE_MOCKS && (
          <div className="mb-5 rounded-lg border border-critical/40 bg-critical/5 px-4 py-3 text-xs font-bold tracking-wider text-critical">
            MOCK DATA — VITE_USE_MOCKS is not "false", so this page
            is showing generated telemetry, not the real backend.
          </div>
        )}

        {/* ================================================================
            FLEET OVERVIEW
            ================================================================ */}

        <SectionCard title="Fleet Network Usage">
          <div className="mb-5 text-[10px] tracking-wider text-muted-foreground">
            AGGREGATED USAGE FROM THE MOST RECENT {FLEET_SAMPLE_WINDOW} NETWORK
            RECORDS — RECORD COUNT, NOT A FIXED TIME DURATION
          </div>

          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard
              label="TOTAL UPLOAD"
              value={formatBytes(fleetTotals.upload)}
            />

            <StatCard
              label="TOTAL DOWNLOAD"
              value={formatBytes(fleetTotals.download)}
            />

            <StatCard
              label="TOTAL TRAFFIC"
              value={formatBytes(fleetTotals.total)}
            />

            <StatCard
              label="MONITORED ENDPOINTS"
              value={String(endpoints.length)}
            />
          </div>

          {fleetQuery.isPending && (
            <div className="mt-6 text-xs text-muted-foreground">
              Loading fleet network telemetry…
            </div>
          )}

          {fleetQuery.isError && (
            <div className="mt-6 flex flex-wrap items-center gap-3 text-xs text-critical">
              <span>
                Could not load fleet network telemetry.{" "}
                {describeError(fleetQuery.error)}
              </span>

              <RetryButton
                onClick={() => void fleetQuery.refetch()}
              />
            </div>
          )}

          {!fleetQuery.isPending &&
            !fleetQuery.isError &&
            fleetSeries.length === 0 && (
              <div className="mt-6 text-xs text-muted-foreground">
                No valid fleet network telemetry is available yet.
              </div>
            )}

          {!fleetQuery.isPending &&
            !fleetQuery.isError &&
            fleetSeries.length > 0 && (
              <div className="mt-6">
                <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="text-sm font-bold text-foreground">
                      Aggregate Fleet Traffic
                    </div>

                    <div className="text-[10px] tracking-wider text-muted-foreground mt-1">
                      UPLOAD + DOWNLOAD ACROSS THE FETCHED RECENT
                      RECORDS
                    </div>
                  </div>

                  <div className="text-[10px] tracking-wider text-muted-foreground">
                    {fleetSeries.length} AGGREGATE POINT
                    {fleetSeries.length === 1 ? "" : "S"}
                  </div>
                </div>

                <div className="h-72">
                  <ResponsiveContainer
                    width="100%"
                    height="100%"
                  >
                    <AreaChart
                      data={fleetChartData}
                      margin={{
                        top: 8,
                        right: 16,
                        left: 8,
                        bottom: 0,
                      }}
                    >
                      <defs>
                        <linearGradient
                          id="fleet-up"
                          x1="0"
                          y1="0"
                          x2="0"
                          y2="1"
                        >
                          <stop
                            offset="0%"
                            stopColor="var(--primary)"
                            stopOpacity={0.5}
                          />

                          <stop
                            offset="100%"
                            stopColor="var(--primary)"
                            stopOpacity={0}
                          />
                        </linearGradient>

                        <linearGradient
                          id="fleet-down"
                          x1="0"
                          y1="0"
                          x2="0"
                          y2="1"
                        >
                          <stop
                            offset="0%"
                            stopColor="var(--critical)"
                            stopOpacity={0.4}
                          />

                          <stop
                            offset="100%"
                            stopColor="var(--critical)"
                            stopOpacity={0}
                          />
                        </linearGradient>
                      </defs>

                      <CartesianGrid
                        stroke="var(--border)"
                        strokeDasharray="2 4"
                      />

                      <XAxis
                        dataKey="ts"
                        type="number"
                        scale="time"
                        domain={["dataMin", "dataMax"]}
                        tickFormatter={formatClock}
                        stroke="var(--muted-foreground)"
                        fontSize={10}
                      />

                      <YAxis
                        stroke="var(--muted-foreground)"
                        fontSize={10}
                        domain={[0, "auto"]}
                        tickFormatter={formatAxisValue}
                        label={{
                          value: `${fleetUnit.unit} / sample`,
                          angle: -90,
                          position: "insideLeft",
                          fontSize: 10,
                          fill: "var(--muted-foreground)",
                        }}
                      />

                      <Tooltip
                        content={<FleetUsageTooltip />}
                      />

                      <Area
                        type="monotone"
                        dataKey="download"
                        stackId="fleet"
                        stroke="var(--critical)"
                        fill="url(#fleet-down)"
                        strokeWidth={2}
                        dot={fleetChartData.length < 3}
                        isAnimationActive={false}
                      />

                      <Area
                        type="monotone"
                        dataKey="upload"
                        stackId="fleet"
                        stroke="var(--primary)"
                        fill="url(#fleet-up)"
                        strokeWidth={2}
                        dot={fleetChartData.length < 3}
                        isAnimationActive={false}
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>

                <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1 text-[10px] tracking-wider text-muted-foreground">
                  <span className="inline-flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-primary" />
                    UPLOAD
                  </span>

                  <span className="inline-flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-critical" />
                    DOWNLOAD
                  </span>

                  <span>STACKED HEIGHT = TOTAL TRAFFIC</span>

                  <span>
                    AGGREGATED BY RECORDED SAMPLE TIMESTAMP
                  </span>
                </div>
              </div>
            )}

          {/* Fleet endpoint table */}
          <div className="mt-8">
            <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
              <div>
                <div className="text-sm font-bold text-foreground">
                  Endpoint Data Usage
                </div>

                <div className="text-[10px] tracking-wider text-muted-foreground mt-1">
                  DEFAULT ORDER: HIGHEST TOTAL USAGE
                </div>
              </div>

              <label className="flex items-center gap-2 text-[10px] tracking-widest text-muted-foreground font-bold">
                SORT

                <select
                  value={fleetSortKey}
                  onChange={(event) =>
                    setFleetSortKey(
                      event.target.value as FleetSortKey,
                    )
                  }
                  className="bg-card border border-border rounded-md px-3 py-1.5 text-xs font-normal tracking-normal text-foreground"
                >
                  <option value="total">
                    TOTAL USAGE
                  </option>

                  <option value="upload">
                    UPLOAD
                  </option>

                  <option value="download">
                    DOWNLOAD
                  </option>

                  <option value="hostname">
                    ENDPOINT
                  </option>
                </select>
              </label>
            </div>

            <div className="overflow-x-auto border border-border rounded-lg">
              <table className="w-full text-xs">
                <thead>
                  <tr className="border-b border-border bg-muted/30 text-[10px] tracking-widest text-muted-foreground">
                    <th className="text-left px-4 py-3 font-bold">
                      #
                    </th>

                    <th className="text-left px-4 py-3 font-bold">
                      ENDPOINT
                    </th>

                    <th className="text-left px-4 py-3 font-bold">
                      STATUS
                    </th>

                    <th className="text-right px-4 py-3 font-bold">
                      UPLOAD
                    </th>

                    <th className="text-right px-4 py-3 font-bold">
                      DOWNLOAD
                    </th>

                    <th className="text-right px-4 py-3 font-bold">
                      TOTAL
                    </th>

                    <th className="text-left px-4 py-3 font-bold">
                      LAST SAMPLE
                    </th>

                    <th className="text-right px-4 py-3 font-bold">
                      ACTION
                    </th>
                  </tr>
                </thead>

                <tbody>
                  {sortedFleetRows.map(
                    (
                      row: FleetNetworkUsageRow,
                      index,
                    ) => (
                      <tr
                        key={row.endpointId}
                        className={`border-b border-border last:border-b-0 hover:bg-muted/20 ${
                          row.endpointId === activeId
                            ? "bg-primary/5"
                            : ""
                        }`}
                      >
                        <td className="px-4 py-3 text-muted-foreground font-mono">
                          {index + 1}
                        </td>

                        <td className="px-4 py-3">
                          <div className="font-bold text-foreground">
                            {row.hostname}
                          </div>

                          {row.endpointId === activeId && (
                            <div className="text-[9px] tracking-widest text-primary mt-0.5">
                              SELECTED
                            </div>
                          )}
                        </td>

                        <td
                          className={`px-4 py-3 font-bold text-[10px] ${statusClass(
                            row.status,
                          )}`}
                        >
                          {row.status}
                        </td>

                        <td className="px-4 py-3 text-right font-mono">
                          {formatBytes(row.upload)}
                        </td>

                        <td className="px-4 py-3 text-right font-mono">
                          {formatBytes(row.download)}
                        </td>

                        <td className="px-4 py-3 text-right font-mono font-bold">
                          {formatBytes(row.total)}
                        </td>

                        <td className="px-4 py-3 text-muted-foreground whitespace-nowrap">
                          {formatDateTime(
                            row.lastSampleAt,
                          )}
                        </td>

                        <td className="px-4 py-3 text-right">
                          <button
                            type="button"
                            onClick={() =>
                              setSelectedId(row.endpointId)
                            }
                            className="border border-border rounded px-2.5 py-1 text-[10px] font-bold tracking-wider text-foreground hover:bg-muted"
                          >
                            VIEW
                          </button>
                        </td>
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            </div>

            {fleetQuery.data?.totalElements !== undefined && (
              <div className="mt-2 text-[10px] tracking-wider text-muted-foreground">
                FETCHED {fleetQuery.data.content.length} OF{" "}
                {fleetQuery.data.totalElements} RECORDED NETWORK
                SAMPLES
              </div>
            )}
          </div>
        </SectionCard>

        <div className="h-5" />

        {/* ================================================================
            ENDPOINT DETAIL
            ================================================================ */}

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-5">
          <StatCard
            label="LAST SAMPLE UPLOAD"
            value={
              summary
                ? formatBytes(summary.latest.sent)
                : "—"
            }
            sub={
              summary
                ? `at ${formatClock(summary.latest.ts)}`
                : undefined
            }
          />

          <StatCard
            label="LAST SAMPLE DOWNLOAD"
            value={
              summary
                ? formatBytes(summary.latest.received)
                : "—"
            }
            sub={
              summary
                ? `at ${formatClock(summary.latest.ts)}`
                : undefined
            }
          />

          <StatCard
            label="TOTAL IN WINDOW"
            value={
              summary
                ? formatBytes(summary.windowTotalBytes)
                : "—"
            }
            sub={
              summary
                ? `${series.points.length} samples · ${formatDuration(
                    summary.spanMs,
                  )}`
                : undefined
            }
          />

          <StatCard
            label="PEAK SAMPLE IN WINDOW"
            value={
              summary
                ? formatBytes(summary.peak.total)
                : "—"
            }
            accent={
              summary
                ? "danger"
                : undefined
            }
            sub={
              summary
                ? `at ${formatClock(summary.peak.ts)}`
                : undefined
            }
          />
        </div>

        <SectionCard
          title={
            activeEndpoint
              ? `Bandwidth per sample — ${activeEndpoint.hostname}`
              : "Bandwidth per sample"
          }
          action={endpointPicker}
        >
          {body}

          {totalRecordedSamples !== undefined &&
            series.points.length > 0 && (
              <div className="mt-2 text-[10px] tracking-wider text-muted-foreground">
                SHOWING {rows?.length ?? 0} MOST RECENT OF{" "}
                {totalRecordedSamples} RECORDED SAMPLES FOR THIS
                ENDPOINT
              </div>
            )}
        </SectionCard>
      </div>
    </AppShell>
  );
}