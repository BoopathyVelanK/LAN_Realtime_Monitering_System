import { createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";
import { useVpnEvents } from "@/api/queries";

export const Route = createFileRoute("/vpn-monitoring")({
  head: () => ({ meta: [{ title: "SecureSOC — VPN Monitoring" }] }),
  component: VPNPage,
});

// RISK column has no backing data - no risk-scoring engine yet (Phase 4).
// KPI cards stay mock/illustrative below - deriving true fleet-wide
// "VPN SERVICES"/"HOSTS WITH VPN" counts would need a distinct-hostname
// aggregate the raw event feed doesn't provide without fetching
// everything; EVENTS LOADED is the honest real count of what's shown.
function VPNPage() {
  const { data, isLoading, isError } = useVpnEvents({ size: 50 });
  const rows = data?.content ?? [];

  return (
    <AppShell title="VPN Monitoring" subtitle="HEURISTIC DETECTION">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[
            { l: "VPN PROCESSES (MOCK)", v: "4", d: true },
            { l: "VPN SERVICES (MOCK)", v: "7" },
            { l: "EVENTS LOADED", v: rows.length },
            { l: "ACTIVE NOW", v: rows.filter((r) => r.active).length, d: true },
          ].map((c, i) => (
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.d ? "text-critical" : ""}`}>{c.v}</div>
            </div>
          ))}
        </div>
        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
              <th className="px-5 py-3 font-bold">HOST</th><th className="px-5 py-3 font-bold">ADAPTER</th><th className="px-5 py-3 font-bold">STATUS</th><th className="px-5 py-3 font-bold">DETECTED</th><th className="px-5 py-3 font-bold">RISK</th>
            </tr></thead>
            <tbody>
              {isLoading && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-xs text-muted-foreground">Loading VPN events…</td></tr>
              )}
              {isError && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-xs text-critical">Could not load VPN events.</td></tr>
              )}
              {!isLoading && !isError && rows.length === 0 && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-xs text-muted-foreground">No VPN activity recorded yet.</td></tr>
              )}
              {rows.map((r) => (
                <tr key={r.id} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs font-bold">{r.hostname}</td>
                  <td className="px-5 py-3 text-xs">{r.adapterName ?? "—"}</td>
                  <td className="px-5 py-3"><span className={`px-2.5 py-1 text-[10px] font-bold tracking-wider rounded ${r.active ? "bg-critical text-critical-foreground" : "border border-border"}`}>{r.active ? "ACTIVE" : "INACTIVE"}</span></td>
                  <td className="px-5 py-3 text-xs">{new Date(r.detectedAt).toLocaleString()}</td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">—</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
