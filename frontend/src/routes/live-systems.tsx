import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SeverityBadge, Meter, StatusDot } from "@/components/AppShell";
import { Search, Filter, RefreshCw } from "lucide-react";
import { useEndpoints } from "@/api/queries";
import { mapEndpointStatus, formatRelativeTime } from "@/lib/endpointFormat";

export const Route = createFileRoute("/live-systems")({
  head: () => ({ meta: [{ title: "SecureSOC — Live Systems" }] }),
  component: LiveSystems,
});

// Frontend integration audit finding: this page previously had ZERO real
// backend wiring and no disclosure comment (unlike every other monitoring
// page in this app) - a hardcoded array of 10 fake hosts rendered
// regardless of what was actually registered. HOSTNAME/USER/STATUS/LAST
// SEEN now come from the same useEndpoints() query as routes/endpoints.tsx
// (TanStack Query dedupes the request, no extra network call). CPU/RAM/
// NETWORK/DATA/VPN/RISK stay mock, explicitly marked (MOCK) below, for
// the same reasons documented in routes/endpoints.tsx's header comment:
// no live resource-usage %, no per-endpoint network/VPN read endpoint,
// and no risk-scoring engine exist on the backend yet.
function mockMetricsFor(seed: string) {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  const cpu = h % 90;
  const ram = (h >> 3) % 90;
  const risk = (["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const)[h % 4];
  const vpn = h % 5 === 0;
  return { cpu, ram, risk, vpn, net: `${((h % 120) / 10).toFixed(1)} MB/s`, data: `${(h % 4000) + 20} MB` };
}

function LiveSystems() {
  const { data: endpoints, isLoading, isError } = useEndpoints();
  const rows = endpoints ?? [];

  return (
    <AppShell title="Live Systems" subtitle="ENDPOINTS · REAL-TIME">
      <div className="px-8 pb-8">
        <div className="bg-card border border-border rounded-lg p-4 flex items-center gap-4 flex-wrap mb-5">
          <div className="flex items-center gap-2 text-xs font-bold tracking-wider"><Filter className="w-4 h-4" /> FILTERS:</div>
          <div className="flex items-center gap-2 border border-border rounded px-3 py-1.5 bg-background">
            <Search className="w-3.5 h-3.5 text-muted-foreground" />
            <input className="bg-transparent outline-none text-xs w-44" placeholder="Search hostname / user" />
          </div>
          <select className="border border-border rounded px-3 py-1.5 text-xs font-bold bg-background"><option>ALL STATUS</option><option>ONLINE</option><option>OFFLINE</option><option>IDLE</option></select>
          <select className="border border-border rounded px-3 py-1.5 text-xs font-bold bg-background"><option>ALL RISK</option><option>CRITICAL</option><option>HIGH</option><option>MEDIUM</option><option>LOW</option></select>
          <button className="ml-auto flex items-center gap-2 text-xs font-bold tracking-wider hover:text-primary"><RefreshCw className="w-3.5 h-3.5" /> RELOAD</button>
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
                <th className="px-5 py-3 font-bold">HOSTNAME</th>
                <th className="px-5 py-3 font-bold">USER</th>
                <th className="px-5 py-3 font-bold">STATUS</th>
                <th className="px-5 py-3 font-bold">CPU (MOCK)</th>
                <th className="px-5 py-3 font-bold">RAM (MOCK)</th>
                <th className="px-5 py-3 font-bold">NETWORK (MOCK)</th>
                <th className="px-5 py-3 font-bold">DATA (MOCK)</th>
                <th className="px-5 py-3 font-bold">VPN (MOCK)</th>
                <th className="px-5 py-3 font-bold">RISK (MOCK)</th>
                <th className="px-5 py-3 font-bold">LAST SEEN</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr><td colSpan={10} className="px-5 py-8 text-center text-xs text-muted-foreground">Loading endpoints…</td></tr>
              )}
              {isError && (
                <tr><td colSpan={10} className="px-5 py-8 text-center text-xs text-critical">Could not load endpoints from the backend.</td></tr>
              )}
              {!isLoading && !isError && rows.length === 0 && (
                <tr><td colSpan={10} className="px-5 py-8 text-center text-xs text-muted-foreground">No endpoints have registered yet.</td></tr>
              )}
              {rows.map((e) => {
                const m = mockMetricsFor(e.id);
                return (
                  <tr key={e.id} className="border-b border-border last:border-0 hover:bg-muted/30 cursor-pointer">
                    <td className="px-5 py-3 text-xs font-bold">{e.hostname}</td>
                    <td className="px-5 py-3 text-xs text-muted-foreground">—</td>
                    <td className="px-5 py-3"><StatusDot status={mapEndpointStatus(e.status)} /></td>
                    <td className="px-5 py-3 min-w-[120px]"><div className="flex items-center gap-2"><span className="text-xs w-8">{m.cpu}%</span><Meter value={m.cpu} accent={m.cpu > 80} /></div></td>
                    <td className="px-5 py-3 min-w-[120px]"><div className="flex items-center gap-2"><span className="text-xs w-8">{m.ram}%</span><Meter value={m.ram} accent={m.ram > 80} /></div></td>
                    <td className="px-5 py-3 text-xs">{m.net}</td>
                    <td className="px-5 py-3 text-xs">{m.data}</td>
                    <td className="px-5 py-3 text-xs">{m.vpn ? <span className="text-critical font-bold">ACTIVE</span> : <span className="text-muted-foreground">—</span>}</td>
                    <td className="px-5 py-3"><SeverityBadge s={m.risk} /></td>
                    <td className="px-5 py-3 text-xs text-muted-foreground">{formatRelativeTime(e.lastHeartbeatAt)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
