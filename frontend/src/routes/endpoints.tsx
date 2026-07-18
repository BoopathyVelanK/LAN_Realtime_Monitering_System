import { createFileRoute, Link } from "@tanstack/react-router";
import { AppShell, KpiGrid, StatusDot } from "@/components/AppShell";
import { Download, Filter } from "lucide-react";
import { useEndpoints } from "@/api/queries";
import { mapEndpointStatus, formatRelativeTime } from "@/lib/endpointFormat";

export const Route = createFileRoute("/endpoints")({
  head: () => ({ meta: [{ title: "SecureSOC — Endpoint Monitoring" }] }),
  component: EndpointsPage,
});

// Columns marked "—" below have no backing data yet:
//   - Student / user assignment: the backend has no student-to-endpoint
//     assignment concept (would be a Phase 6+ feature).
//   - Live CPU/RAM %: EndpointSummaryResponse only exposes static specs
//     (ramMb total, cpuInfo string) - live usage % is sent in agent
//     heartbeats but never persisted/exposed via a GET endpoint.
//   - RISK: no risk-scoring engine yet (Phase 4 detection engine).
//   - USB / VPN: agent sends these events (POST /monitoring/usb,
//     /monitoring/vpn) but there is no GET endpoint to read them back -
//     MonitoringController is ingest-only. See dashboardApi.ts header.
function EndpointsPage() {
  const { data: endpoints, isLoading, isError } = useEndpoints();
  const rows = endpoints ?? [];

  const total = rows.length;
  const online = rows.filter((e) => e.status === "ONLINE").length;
  const offline = total - online;

  return (
    <AppShell title="Endpoint Monitoring" subtitle="LAN AGENT FLEET">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l: "TOTAL ENDPOINTS", v: total },
          { l: "ONLINE", v: online, p: true },
          { l: "OFFLINE", v: offline, d: offline > 0 },
          // No risk-scoring engine yet (Phase 4) - see comment above.
          { l: "HIGH RISK", v: "—", d: true },
        ]} />

        <div className="flex flex-wrap items-center gap-3 mb-4">
          <input placeholder="Search hostname / student / IP…" className="flex-1 min-w-64 border border-border bg-card rounded px-3 py-2 text-xs outline-none focus:border-primary" />
          {["Department", "Lab", "OS", "Status", "Risk ≥"].map((f) => (
            <button key={f} className="inline-flex items-center gap-1.5 border border-border rounded px-3 py-2 text-[10px] font-bold tracking-widest hover:bg-muted">
              <Filter className="w-3 h-3" /> {f}
            </button>
          ))}
          <button className="inline-flex items-center gap-1.5 bg-primary text-primary-foreground rounded px-3 py-2 text-[10px] font-bold tracking-widest">
            <Download className="w-3 h-3" /> EXPORT CSV
          </button>
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
                {["HOST", "STUDENT", "DEPT", "LAB", "IP / MAC", "USER", "CPU", "RAM", "RISK", "USB", "VPN", "STATUS", "HEARTBEAT", "ACTION"].map((c) =>
                  <th key={c} className="px-4 py-3 font-bold">{c}</th>
                )}
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr><td colSpan={14} className="px-4 py-8 text-center text-xs text-muted-foreground">Loading endpoints…</td></tr>
              )}
              {isError && (
                <tr><td colSpan={14} className="px-4 py-8 text-center text-xs text-critical">Could not load endpoints from the backend.</td></tr>
              )}
              {!isLoading && !isError && rows.length === 0 && (
                <tr><td colSpan={14} className="px-4 py-8 text-center text-xs text-muted-foreground">No endpoints have registered yet.</td></tr>
              )}
              {rows.map((e) => (
                <tr key={e.id} className="border-b border-border last:border-0 hover:bg-muted/30">
                  <td className="px-4 py-3 text-xs font-bold">{e.hostname}</td>
                  <td className="px-4 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-4 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-4 py-3 text-xs">{e.labName ?? "Unassigned"}</td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground"><div>{e.ipAddress}</div><div>{e.macAddress}</div></td>
                  <td className="px-4 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-4 py-3 w-24 text-xs text-muted-foreground">—</td>
                  <td className="px-4 py-3 w-24 text-xs text-muted-foreground">—</td>
                  <td className="px-4 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-4 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-4 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-4 py-3"><StatusDot status={mapEndpointStatus(e.status)} /></td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground">{formatRelativeTime(e.lastHeartbeatAt)}</td>
                  <td className="px-4 py-3">
                    <Link to="/endpoints/$id" params={{ id: e.hostname }} className="text-[10px] font-bold tracking-widest bg-primary text-primary-foreground px-3 py-1.5 rounded">
                      OPEN
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex justify-between items-center px-5 py-3 text-[11px] text-muted-foreground border-t border-border">
            <span>Showing {rows.length} of {total} endpoints</span>
            <div className="flex gap-2">
              {["‹", "1", "2", "3", "…", "10", "›"].map((p, i) => (
                <button key={i} className={`px-2.5 py-1 rounded text-[11px] font-bold ${p === "1" ? "bg-primary text-primary-foreground" : "border border-border hover:bg-muted"}`}>{p}</button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
