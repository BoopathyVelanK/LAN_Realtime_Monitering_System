import { createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";
import { Usb } from "lucide-react";
import { useUsbEvents } from "@/api/queries";

export const Route = createFileRoute("/usb-monitoring")({
  head: () => ({ meta: [{ title: "SecureSOC — USB Monitoring" }] }),
  component: USBMonitoring,
});

// Columns marked "—" have no backing data: the backend has no user
// session tracking on UsbEvent, and no whitelist/severity concept (that's
// Phase 4 detection engine work) - so USER/SEVERITY/NOTE can't be real
// yet. VENDOR shows the raw USB vendor ID (e.g. "0781") rather than a
// resolved brand name like "SanDisk" - collector.py doesn't ship a
// USB vendor-ID database, so there's nothing to resolve it against.
// KPI cards below and the alert banner remain mock/illustrative - there's
// no aggregate "last 24h" endpoint, and "unauthorized" isn't a concept
// the backend has (no whitelist engine yet).
function USBMonitoring() {
  const { data, isLoading, isError } = useUsbEvents({ size: 50 });
  const events = data?.content ?? [];

  return (
    <AppShell title="USB Monitoring" subtitle="REMOVABLE MEDIA">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {/* MOCK KPIs - no 24h-aggregate or whitelist-engine endpoint yet. */}
          {[
            { l: "INSERTIONS / 24H (MOCK)", v: "38" },
            { l: "UNAUTHORIZED (MOCK)", v: "4", danger: true },
            { l: "EVENTS LOADED", v: events.length },
            { l: "WHITELISTED VENDORS (MOCK)", v: "12" },
          ].map((c, i) => (
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.danger ? "text-critical" : ""}`}>{c.v}</div>
            </div>
          ))}
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
                <th className="px-5 py-3 font-bold">TIMESTAMP</th>
                <th className="px-5 py-3 font-bold">ACTION</th>
                <th className="px-5 py-3 font-bold">VENDOR ID</th>
                <th className="px-5 py-3 font-bold">DEVICE</th>
                <th className="px-5 py-3 font-bold">HOST</th>
                <th className="px-5 py-3 font-bold">USER</th>
                <th className="px-5 py-3 font-bold">SEVERITY</th>
                <th className="px-5 py-3 font-bold">NOTE</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr><td colSpan={8} className="px-5 py-8 text-center text-xs text-muted-foreground">Loading USB events…</td></tr>
              )}
              {isError && (
                <tr><td colSpan={8} className="px-5 py-8 text-center text-xs text-critical">Could not load USB events.</td></tr>
              )}
              {!isLoading && !isError && events.length === 0 && (
                <tr><td colSpan={8} className="px-5 py-8 text-center text-xs text-muted-foreground">No USB events recorded yet.</td></tr>
              )}
              {events.map((e) => (
                <tr key={e.id} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs">{new Date(e.eventTime).toLocaleString()}</td>
                  <td className="px-5 py-3">
                    <span className={`inline-flex items-center gap-1.5 text-[10px] font-bold tracking-wider px-2 py-1 rounded border ${e.action === "CONNECTED" ? "border-primary text-primary" : "border-border text-muted-foreground"}`}>
                      <Usb className="w-3 h-3" /> {e.action ?? "UNKNOWN"}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-xs font-bold">{e.vendorId ?? "—"}</td>
                  <td className="px-5 py-3 text-xs">{e.deviceName ?? "—"}</td>
                  <td className="px-5 py-3 text-xs font-bold">{e.hostname}</td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">—</td>
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
