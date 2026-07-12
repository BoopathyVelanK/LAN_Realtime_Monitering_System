import { createFileRoute, Link } from "@tanstack/react-router";
import { AppShell, KpiGrid, SeverityBadge, StatusDot, Meter } from "@/components/AppShell";
import { Download, Filter } from "lucide-react";

export const Route = createFileRoute("/endpoints")({
  head: () => ({ meta: [{ title: "SecureSOC — Endpoint Monitoring" }] }),
  component: EndpointsPage,
});

const endpoints = Array.from({ length: 14 }, (_, i) => {
  const id = String(i + 1).padStart(2, "0");
  const cpu = 20 + ((i * 13) % 70);
  const ram = 30 + ((i * 17) % 60);
  const risk = ((i * 23) % 100);
  return {
    host: `LAB-PC-${id}`,
    student: ["A. Sharma", "R. Iyer", "M. Khan", "P. Das", "S. Nair", "T. Rao", "K. Menon", "J. Verma"][i % 8],
    dept: ["CSE", "IT", "ECE", "EEE"][i % 4],
    lab: ["Lab-A", "Lab-B", "Lab-C"][i % 3],
    ip: `10.0.4.${10 + i}`,
    mac: `00:1A:2B:3C:4D:${(0x10 + i).toString(16).toUpperCase().padStart(2, "0")}`,
    os: "Windows 11 Pro",
    user: `student_${20 + i}`,
    cpu, ram,
    risk,
    usb: i % 5 === 0 ? "ALERT" : "OK",
    vpn: i % 7 === 0 ? "DETECTED" : "OFF",
    status: (i % 9 === 0 ? "offline" : i % 4 === 0 ? "idle" : "online") as "online" | "offline" | "idle",
    beat: i % 9 === 0 ? "1h 12m ago" : "just now",
  };
});

function EndpointsPage() {
  return (
    <AppShell title="Endpoint Monitoring" subtitle="LAN AGENT FLEET">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l: "TOTAL ENDPOINTS", v: 142 },
          { l: "ONLINE", v: 128, p: true },
          { l: "OFFLINE", v: 14, d: true },
          { l: "HIGH RISK", v: 12, d: true },
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
              {endpoints.map((e, i) => (
                <tr key={i} className="border-b border-border last:border-0 hover:bg-muted/30">
                  <td className="px-4 py-3 text-xs font-bold">{e.host}</td>
                  <td className="px-4 py-3 text-xs">{e.student}</td>
                  <td className="px-4 py-3 text-xs">{e.dept}</td>
                  <td className="px-4 py-3 text-xs">{e.lab}</td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground"><div>{e.ip}</div><div>{e.mac}</div></td>
                  <td className="px-4 py-3 text-xs">{e.user}</td>
                  <td className="px-4 py-3 w-24"><div className="text-[10px] mb-1 font-bold">{e.cpu}%</div><Meter value={e.cpu} accent={e.cpu > 80} /></td>
                  <td className="px-4 py-3 w-24"><div className="text-[10px] mb-1 font-bold">{e.ram}%</div><Meter value={e.ram} /></td>
                  <td className="px-4 py-3">
                    <SeverityBadge s={e.risk > 80 ? "CRITICAL" : e.risk > 60 ? "HIGH" : e.risk > 30 ? "MEDIUM" : "LOW"} />
                  </td>
                  <td className="px-4 py-3">
                    {e.usb === "ALERT"
                      ? <span className="px-2 py-0.5 bg-critical text-critical-foreground text-[10px] font-bold rounded tracking-wider">ALERT</span>
                      : <span className="text-[10px] text-muted-foreground font-bold tracking-wider">OK</span>}
                  </td>
                  <td className="px-4 py-3">
                    {e.vpn === "DETECTED"
                      ? <span className="px-2 py-0.5 border border-critical text-critical text-[10px] font-bold rounded tracking-wider">VPN</span>
                      : <span className="text-[10px] text-muted-foreground font-bold tracking-wider">—</span>}
                  </td>
                  <td className="px-4 py-3"><StatusDot status={e.status} /></td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground">{e.beat}</td>
                  <td className="px-4 py-3">
                    <Link to="/endpoints/$id" params={{ id: e.host }} className="text-[10px] font-bold tracking-widest bg-primary text-primary-foreground px-3 py-1.5 rounded">
                      OPEN
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="flex justify-between items-center px-5 py-3 text-[11px] text-muted-foreground border-t border-border">
            <span>Showing 1–14 of 142 endpoints</span>
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
