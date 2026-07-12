import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SeverityBadge } from "@/components/AppShell";
import { Usb, AlertTriangle } from "lucide-react";

export const Route = createFileRoute("/usb-monitoring")({
  head: () => ({ meta: [{ title: "SecureSOC — USB Monitoring" }] }),
  component: USBMonitoring,
});

const events = [
  { ts: "14:02:11", action: "INSERT", vendor: "SanDisk", device: "Cruzer Blade 16GB", host: "LAB-PC-17", user: "student_42", sev: "CRITICAL" as const, note: "Unauthorized device" },
  { ts: "13:42:08", action: "REMOVE", vendor: "Logitech", device: "USB Receiver", host: "LAB-PC-04", user: "student_07", sev: "INFO" as const, note: "—" },
  { ts: "13:12:55", action: "INSERT", vendor: "Kingston", device: "DataTraveler 32GB", host: "LAB-PC-22", user: "student_11", sev: "HIGH" as const, note: "Unknown vendor" },
  { ts: "12:48:21", action: "INSERT", vendor: "Logitech", device: "USB Receiver", host: "LAB-PC-04", user: "student_07", sev: "INFO" as const, note: "—" },
  { ts: "12:11:19", action: "INSERT", vendor: "Unknown",  device: "Mass Storage",    host: "LAB-PC-09", user: "student_88", sev: "CRITICAL" as const, note: "Vendor not whitelisted" },
  { ts: "11:55:02", action: "REMOVE", vendor: "SanDisk",  device: "Cruzer Blade 16GB", host: "LAB-PC-17", user: "student_42", sev: "INFO" as const, note: "—" },
];

function USBMonitoring() {
  return (
    <AppShell title="USB Monitoring" subtitle="REMOVABLE MEDIA">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[
            { l: "INSERTIONS / 24H", v: "38" },
            { l: "UNAUTHORIZED", v: "4", danger: true },
            { l: "UNIQUE DEVICES", v: "21" },
            { l: "WHITELISTED VENDORS", v: "12" },
          ].map((c, i) => (
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.danger ? "text-critical" : ""}`}>{c.v}</div>
            </div>
          ))}
        </div>

        <div className="bg-primary/10 border border-primary rounded-lg p-4 flex items-start gap-3 mb-5">
          <AlertTriangle className="w-5 h-5 text-primary shrink-0 mt-0.5" />
          <div className="text-xs">
            <div className="font-bold text-primary">UNAUTHORIZED USB ALERT</div>
            <div className="mt-1 text-foreground">SanDisk Cruzer Blade 16GB inserted on <span className="font-bold">LAB-PC-17</span> by <span className="font-bold">student_42</span> at 14:02:11. Vendor is not on whitelist.</div>
          </div>
          <button className="ml-auto text-[10px] font-bold tracking-wider bg-primary text-primary-foreground px-3 py-2 rounded">ACKNOWLEDGE</button>
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
                <th className="px-5 py-3 font-bold">TIMESTAMP</th>
                <th className="px-5 py-3 font-bold">ACTION</th>
                <th className="px-5 py-3 font-bold">VENDOR</th>
                <th className="px-5 py-3 font-bold">DEVICE</th>
                <th className="px-5 py-3 font-bold">HOST</th>
                <th className="px-5 py-3 font-bold">USER</th>
                <th className="px-5 py-3 font-bold">SEVERITY</th>
                <th className="px-5 py-3 font-bold">NOTE</th>
              </tr>
            </thead>
            <tbody>
              {events.map((e, i) => (
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs">{e.ts}</td>
                  <td className="px-5 py-3">
                    <span className={`inline-flex items-center gap-1.5 text-[10px] font-bold tracking-wider px-2 py-1 rounded border ${e.action === "INSERT" ? "border-primary text-primary" : "border-border text-muted-foreground"}`}>
                      <Usb className="w-3 h-3" /> {e.action}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-xs font-bold">{e.vendor}</td>
                  <td className="px-5 py-3 text-xs">{e.device}</td>
                  <td className="px-5 py-3 text-xs font-bold">{e.host}</td>
                  <td className="px-5 py-3 text-xs">{e.user}</td>
                  <td className="px-5 py-3"><SeverityBadge s={e.sev} /></td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">{e.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
