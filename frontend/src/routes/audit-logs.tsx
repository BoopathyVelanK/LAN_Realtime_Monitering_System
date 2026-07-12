import { createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";

export const Route = createFileRoute("/audit-logs")({
  head: () => ({ meta: [{ title: "SecureSOC — Audit Logs" }] }),
  component: AuditLogs,
});

const logs = [
  { ts: "2025-10-24 14:02:11", fac: "faculty_amin", host: "FAC-DC-MAIN", action: "Exam Mode Enabled (LAB-A)", status: "OK", ip: "10.0.1.55" },
  { ts: "2025-10-24 13:58:44", fac: "faculty_amin", host: "FAC-DC-MAIN", action: "Passkey Verified", status: "OK", ip: "10.0.1.55" },
  { ts: "2025-10-24 13:45:01", fac: "faculty_rao", host: "FAC-DC-MAIN", action: "Failed Passkey Attempt", status: "FAIL", ip: "10.0.1.61" },
  { ts: "2025-10-24 13:20:12", fac: "system", host: "LAB-PC-17", action: "USB Alert Raised", status: "OK", ip: "10.0.4.17" },
  { ts: "2025-10-24 12:48:21", fac: "system", host: "LAB-PC-04", action: "VPN Alert Raised", status: "OK", ip: "10.0.4.04" },
  { ts: "2025-10-24 11:02:43", fac: "system", host: "LAB-PC-31", action: "Offline Sync Resumed (queued: 412 events)", status: "OK", ip: "10.0.4.31" },
  { ts: "2025-10-24 09:01:00", fac: "faculty_amin", host: "FAC-DC-MAIN", action: "Faculty Login", status: "OK", ip: "10.0.1.55" },
];

function AuditLogs() {
  return (
    <AppShell title="Audit Logs" subtitle="IMMUTABLE TRAIL">
      <div className="px-8 pb-8">
        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
              <th className="px-5 py-3 font-bold">TIMESTAMP</th><th className="px-5 py-3 font-bold">FACULTY</th><th className="px-5 py-3 font-bold">HOST</th><th className="px-5 py-3 font-bold">ACTION</th><th className="px-5 py-3 font-bold">STATUS</th><th className="px-5 py-3 font-bold">IP</th>
            </tr></thead>
            <tbody>
              {logs.map((l,i)=>(
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs">{l.ts}</td>
                  <td className="px-5 py-3 text-xs font-bold">{l.fac}</td>
                  <td className="px-5 py-3 text-xs">{l.host}</td>
                  <td className="px-5 py-3 text-xs">{l.action}</td>
                  <td className="px-5 py-3"><span className={`px-2.5 py-1 text-[10px] font-bold tracking-wider rounded ${l.status==="FAIL"?"bg-critical text-critical-foreground":"border border-border text-muted-foreground"}`}>{l.status}</span></td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">{l.ip}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
