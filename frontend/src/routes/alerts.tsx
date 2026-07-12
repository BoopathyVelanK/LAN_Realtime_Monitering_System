import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SeverityBadge } from "@/components/AppShell";
import { CheckCircle2, AlertTriangle } from "lucide-react";

export const Route = createFileRoute("/alerts")({
  head: () => ({ meta: [{ title: "SecureSOC — Alerts" }] }),
  component: AlertsPage,
});

const alerts = [
  { ts: "2025-10-24 14:02:11", sev: "CRITICAL" as const, host: "LAB-PC-17", user: "student_42", desc: "Unauthorized USB inserted (SanDisk Cruzer 16GB)", status: "OPEN" },
  { ts: "2025-10-24 13:58:44", sev: "HIGH" as const, host: "LAB-PC-22", user: "student_11", desc: "Idle exceeded 30 minutes (critical idle)", status: "OPEN" },
  { ts: "2025-10-24 13:45:01", sev: "CRITICAL" as const, host: "FAC-DC-MAIN", user: "admin_root", desc: "Lateral movement indicator across LAN segment", status: "ACK" },
  { ts: "2025-10-24 13:20:12", sev: "MEDIUM" as const, host: "LAB-PC-04", user: "student_07", desc: "VPN process detected: WireGuard", status: "OPEN" },
  { ts: "2025-10-24 12:48:21", sev: "HIGH" as const, host: "LAB-PC-09", user: "student_88", desc: "Excessive upload — 412 MB in 10 minutes", status: "ACK" },
  { ts: "2025-10-24 12:11:19", sev: "CRITICAL" as const, host: "LAB-PC-09", user: "student_88", desc: "Blacklisted application running: uTorrent.exe", status: "OPEN" },
  { ts: "2025-10-24 11:02:43", sev: "WARNING" as const, host: "LAB-PC-31", user: "—", desc: "Offline > 1h — LAN sync pending", status: "OPEN" },
];

function AlertsPage() {
  return (
    <AppShell title="Alert Center" subtitle="ACTIVE NOTIFICATIONS">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[{l:"OPEN",v:"5",d:true},{l:"ACKNOWLEDGED",v:"2"},{l:"RESOLVED 24H",v:"18"},{l:"AVG RESPONSE",v:"4m 12s"}].map((c,i)=>(
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.d?"text-critical":""}`}>{c.v}</div>
            </div>
          ))}
        </div>
        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
              <th className="px-5 py-3 font-bold">TIMESTAMP</th><th className="px-5 py-3 font-bold">SEVERITY</th><th className="px-5 py-3 font-bold">HOST</th><th className="px-5 py-3 font-bold">USER</th><th className="px-5 py-3 font-bold">DESCRIPTION</th><th className="px-5 py-3 font-bold">STATUS</th><th className="px-5 py-3 font-bold">ACTION</th>
            </tr></thead>
            <tbody>
              {alerts.map((a,i)=>(
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs">{a.ts}</td>
                  <td className="px-5 py-3"><SeverityBadge s={a.sev} /></td>
                  <td className="px-5 py-3 text-xs font-bold">{a.host}</td>
                  <td className="px-5 py-3 text-xs">{a.user}</td>
                  <td className="px-5 py-3 text-xs">{a.desc}</td>
                  <td className="px-5 py-3">
                    {a.status === "ACK"
                      ? <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground"><CheckCircle2 className="w-3.5 h-3.5"/>ACKNOWLEDGED</span>
                      : <span className="inline-flex items-center gap-1.5 text-xs text-critical font-bold"><AlertTriangle className="w-3.5 h-3.5"/>OPEN</span>}
                  </td>
                  <td className="px-5 py-3">
                    {a.status === "OPEN" && <button className="text-[10px] font-bold tracking-wider bg-primary text-primary-foreground px-3 py-1.5 rounded">ACKNOWLEDGE</button>}
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
