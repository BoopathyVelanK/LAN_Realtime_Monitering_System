import { createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";
import { Usb } from "lucide-react";
import { useUsbEvents } from "@/api/queries";

export const Route = createFileRoute("/usb-monitoring")({
  head: () => ({ meta: [{ title: "SecureSOC — USB Monitoring" }] }),
  component: USBMonitoring,
});

// USER column has no backing data: the backend has no user session
// tracking on UsbEvent (that's only on login/logout events, which don't
// cross-reference USB events at the DB level).
// VENDOR shows the raw USB vendor ID (e.g. "0781") - collector.py
// doesn't ship a USB vendor-ID database so there's nothing to resolve
// it against.
// INSERTIONS/24H and CONNECTED counts are derived client-side from
// the current loaded page (size=50, newest first). The backend has no
// aggregate/stats endpoint for USB yet — totalElements is the real
// backend-reported fleet-wide total (from PageResponse<UsbEventResponse>).
// "UNAUTHORIZED" and "WHITELISTED VENDORS" have no backend concept (no
// whitelist engine yet) — they are removed to avoid false data.
function USBMonitoring() {
  const { data, isLoading, isError } = useUsbEvents({ size: 50 });
  const events = data?.content ?? [];

  const now = Date.now();
  const h24ago = now - 24 * 60 * 60 * 1000;
  const insertions24h = events.filter(
    (e) => e.action === "CONNECTED" && new Date(e.eventTime).getTime() >= h24ago,
  ).length;
  const connectedCount = events.filter((e) => e.action === "CONNECTED").length;
  const totalElements = data?.totalElements ?? null;

  return (
    <AppShell title="USB Monitoring" subtitle="REMOVABLE MEDIA">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-3 gap-4 mb-5">
          {[
            { l: "CONNECTED / 24H (PAGE)", v: isLoading ? "…" : String(insertions24h) },
            { l: "CONNECTED IN PAGE", v: isLoading ? "…" : String(connectedCount) },
            {
              l: "TOTAL EVENTS (BACKEND)",
              v: isLoading ? "…" : totalElements !== null ? String(totalElements) : "—",
            },
          ].map((c, i) => (
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className="text-3xl font-bold mt-3">{c.v}</div>
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
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-xs text-muted-foreground">Loading USB events…</td></tr>
              )}
              {isError && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-xs text-critical">Could not load USB events.</td></tr>
              )}
              {!isLoading && !isError && events.length === 0 && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-xs text-muted-foreground">No USB events recorded yet.</td></tr>
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
