import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SectionCard } from "@/components/AppShell";
import { AreaChart, Area, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Tooltip } from "recharts";
import { useNetworkUsageEvents } from "@/api/queries";

export const Route = createFileRoute("/network-usage")({
  head: () => ({ meta: [{ title: "SecureSOC — Network Usage" }] }),
  component: NetworkUsage,
});

function toMBps(bytes: number) {
  return Math.round((bytes / (1024 * 1024)) * 100) / 100;
}

// This is a raw, fleet-wide sample feed (every endpoint's samples
// interleaved, newest first from the backend, reversed here to
// chronological order for the chart) - not a true per-minute aggregate
// sum across all endpoints, since MonitoringController has no
// aggregation endpoint, only the raw event list. With multiple endpoints
// reporting on their own independent monitoring_interval_seconds cycles,
// the x-axis is "most recent N samples" rather than a clean fixed
// timeline. PEAK 24H and BANDWIDTH SPIKES stay mock below since neither
// is computable from a bounded recent-sample fetch.
function NetworkUsage() {
  const { data, isLoading, isError } = useNetworkUsageEvents({ size: 30 });
  const series = [...(data?.content ?? [])].reverse().map((e) => ({
    t: new Date(e.recordedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
    up: toMBps(e.bytesSent),
    down: toMBps(e.bytesReceived),
  }));
  const latest = data?.content[0];

  return (
    <AppShell title="Network Usage" subtitle="BANDWIDTH TELEMETRY">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[
            { l: "LAST SAMPLE UPLOAD", v: latest ? `${toMBps(latest.bytesSent)} MB` : "—" },
            { l: "LAST SAMPLE DOWNLOAD", v: latest ? `${toMBps(latest.bytesReceived)} MB` : "—" },
            { l: "PEAK 24H (MOCK)", v: "912 MB/s" },
            { l: "BANDWIDTH SPIKES (MOCK)", v: "3", d: true },
          ].map((c, i) => (
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.d ? "text-critical" : ""}`}>{c.v}</div>
            </div>
          ))}
        </div>
        <SectionCard title={isLoading ? "LAN Bandwidth (loading…)" : isError ? "LAN Bandwidth (failed to load)" : "LAN Bandwidth (most recent samples, all endpoints)"}>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={series}>
                <defs>
                  <linearGradient id="up2" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--primary)" stopOpacity={0.5}/><stop offset="100%" stopColor="var(--primary)" stopOpacity={0}/></linearGradient>
                  <linearGradient id="dn2" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--critical)" stopOpacity={0.4}/><stop offset="100%" stopColor="var(--critical)" stopOpacity={0}/></linearGradient>
                </defs>
                <CartesianGrid stroke="var(--border)" strokeDasharray="2 4"/>
                <XAxis dataKey="t" stroke="var(--muted-foreground)" fontSize={10}/>
                <YAxis stroke="var(--muted-foreground)" fontSize={10} label={{ value: "MB / sample", angle: -90, position: "insideLeft", fontSize: 10, fill: "var(--muted-foreground)" }}/>
                <Tooltip contentStyle={{background:"var(--card)",border:"1px solid var(--border)",fontSize:12}}/>
                <Area type="monotone" dataKey="down" stroke="var(--critical)" fill="url(#dn2)" strokeWidth={2}/>
                <Area type="monotone" dataKey="up" stroke="var(--primary)" fill="url(#up2)" strokeWidth={2}/>
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </SectionCard>
      </div>
    </AppShell>
  );
}
