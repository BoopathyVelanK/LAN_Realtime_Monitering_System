import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SeverityBadge } from "@/components/AppShell";
import { CheckCircle2, AlertTriangle, Loader2 } from "lucide-react";
import { useAlerts, useAcknowledgeAlert, useResolveAlert } from "@/api/queries";

export const Route = createFileRoute("/alerts")({
  head: () => ({ meta: [{ title: "SecureSOC — Alerts" }] }),
  component: AlertsPage,
});

// MOCK DATA (Phase 4A audit): no AlertController/Alert entity exists on
// the backend yet (Phase 4 detection engine). useAlerts() below always
// resolves from mocks/data.ts regardless of VITE_USE_MOCKS - see
// dashboardApi.ts's header comment. This page used to keep its own
// separate hardcoded array; it now goes through the same query layer as
// every other page so there's a single mock data source, not two.
function AlertsPage() {
  const { data: alerts, isLoading, isError } = useAlerts();
  const acknowledge = useAcknowledgeAlert();
  const resolve = useResolveAlert();
  const rows = alerts ?? [];

  const open = rows.filter((a) => a.status === "OPEN").length;
  const acknowledged = rows.filter((a) => a.status === "ACKNOWLEDGED").length;
  const resolved = rows.filter((a) => a.status === "RESOLVED").length;

  return (
    <AppShell title="Alert Center" subtitle="ACTIVE NOTIFICATIONS">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          <div className="bg-card border border-border rounded-lg p-5">
            <div className="text-[10px] tracking-widest text-muted-foreground font-bold">OPEN</div>
            <div className="text-3xl font-bold mt-3 text-critical">{isLoading ? "—" : open}</div>
          </div>
          <div className="bg-card border border-border rounded-lg p-5">
            <div className="text-[10px] tracking-widest text-muted-foreground font-bold">ACKNOWLEDGED</div>
            <div className="text-3xl font-bold mt-3">{isLoading ? "—" : acknowledged}</div>
          </div>
          <div className="bg-card border border-border rounded-lg p-5">
            <div className="text-[10px] tracking-widest text-muted-foreground font-bold">RESOLVED</div>
            <div className="text-3xl font-bold mt-3">{isLoading ? "—" : resolved}</div>
          </div>
          {/* No timestamped acknowledge/resolve audit trail to compute a
              real average from yet - mock stat, marked here explicitly. */}
          <div className="bg-card border border-border rounded-lg p-5">
            <div className="text-[10px] tracking-widest text-muted-foreground font-bold">AVG RESPONSE (MOCK)</div>
            <div className="text-3xl font-bold mt-3">4m 12s</div>
          </div>
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
                <th className="px-5 py-3 font-bold">TIMESTAMP</th>
                <th className="px-5 py-3 font-bold">SEVERITY</th>
                <th className="px-5 py-3 font-bold">HOST</th>
                <th className="px-5 py-3 font-bold">DESCRIPTION</th>
                <th className="px-5 py-3 font-bold">STATUS</th>
                <th className="px-5 py-3 font-bold">ACTION</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr><td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">Loading alerts…</td></tr>
              )}
              {isError && (
                <tr><td colSpan={6} className="px-5 py-8 text-center text-xs text-critical">Could not load alerts.</td></tr>
              )}
              {!isLoading && !isError && rows.length === 0 && (
                <tr><td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">No alerts.</td></tr>
              )}
              {rows.map((a) => (
                <tr key={a.id} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs">{new Date(a.createdAt).toLocaleString()}</td>
                  <td className="px-5 py-3"><SeverityBadge s={a.severity as "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO"} /></td>
                  <td className="px-5 py-3 text-xs font-bold">{a.hostname}</td>
                  <td className="px-5 py-3 text-xs">{a.title}</td>
                  <td className="px-5 py-3">
                    {a.status === "OPEN" ? (
                      <span className="inline-flex items-center gap-1.5 text-xs text-critical font-bold"><AlertTriangle className="w-3.5 h-3.5" />OPEN</span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground"><CheckCircle2 className="w-3.5 h-3.5" />{a.status}</span>
                    )}
                  </td>
                  <td className="px-5 py-3">
                    {a.status === "OPEN" && (
                      <button
                        onClick={() => acknowledge.mutate(a.id)}
                        disabled={acknowledge.isPending || resolve.isPending}
                        className="inline-flex items-center gap-1.5 text-[10px] font-bold tracking-wider bg-primary text-primary-foreground px-3 py-1.5 rounded disabled:opacity-60"
                      >
                        {acknowledge.isPending && acknowledge.variables === a.id && <Loader2 className="w-3 h-3 animate-spin" />}
                        ACKNOWLEDGE
                      </button>
                    )}
                    {a.status === "ACKNOWLEDGED" && (
                      <button
                        onClick={() => resolve.mutate(a.id)}
                        disabled={resolve.isPending || acknowledge.isPending}
                        className="inline-flex items-center gap-1.5 text-[10px] font-bold tracking-wider bg-muted text-foreground px-3 py-1.5 rounded disabled:opacity-60"
                      >
                        {resolve.isPending && resolve.variables === a.id && <Loader2 className="w-3 h-3 animate-spin" />}
                        RESOLVE
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
