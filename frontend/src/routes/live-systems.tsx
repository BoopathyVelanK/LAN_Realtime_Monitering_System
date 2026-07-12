import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SeverityBadge, Meter, StatusDot } from "@/components/AppShell";
import { Search, Filter, RefreshCw } from "lucide-react";

export const Route = createFileRoute("/live-systems")({
  head: () => ({ meta: [{ title: "SecureSOC — Live Systems" }] }),
  component: LiveSystems,
});

type Sys = {
  host: string; user: string; status: "online" | "offline" | "idle" | "exam";
  cpu: number; ram: number; net: string; data: string; vpn: boolean;
  risk: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW"; seen: string;
};

const systems: Sys[] = [
  { host: "LAB-PC-01", user: "student_01", status: "online", cpu: 32, ram: 48, net: "1.2 MB/s", data: "412 MB", vpn: false, risk: "LOW", seen: "now" },
  { host: "LAB-PC-04", user: "student_07", status: "online", cpu: 71, ram: 82, net: "8.4 MB/s", data: "1.8 GB", vpn: true,  risk: "MEDIUM", seen: "now" },
  { host: "LAB-PC-09", user: "student_88", status: "online", cpu: 88, ram: 76, net: "12.1 MB/s", data: "3.2 GB", vpn: false, risk: "HIGH", seen: "now" },
  { host: "LAB-PC-12", user: "—",         status: "idle",   cpu: 4,  ram: 22, net: "0.0 MB/s", data: "82 MB",  vpn: false, risk: "LOW", seen: "12m" },
  { host: "LAB-PC-17", user: "student_42", status: "online", cpu: 41, ram: 55, net: "3.1 MB/s", data: "910 MB", vpn: false, risk: "CRITICAL", seen: "now" },
  { host: "LAB-PC-22", user: "student_11", status: "idle",   cpu: 2,  ram: 18, net: "0.0 MB/s", data: "55 MB",  vpn: false, risk: "MEDIUM", seen: "34m" },
  { host: "LAB-PC-29", user: "student_45", status: "online", cpu: 19, ram: 34, net: "0.5 MB/s", data: "201 MB", vpn: false, risk: "LOW", seen: "now" },
  { host: "FAC-DC-MAIN", user: "admin_root", status: "exam", cpu: 12, ram: 28, net: "0.2 MB/s", data: "1.1 GB", vpn: false, risk: "CRITICAL", seen: "now" },
  { host: "LAB-PC-31", user: "—",         status: "offline", cpu: 0, ram: 0, net: "—",         data: "—",      vpn: false, risk: "MEDIUM", seen: "2h 14m" },
  { host: "LAB-PC-33", user: "student_19", status: "online", cpu: 26, ram: 51, net: "0.9 MB/s", data: "302 MB", vpn: false, risk: "LOW", seen: "now" },
];

function LiveSystems() {
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
                <th className="px-5 py-3 font-bold">CPU</th>
                <th className="px-5 py-3 font-bold">RAM</th>
                <th className="px-5 py-3 font-bold">NETWORK</th>
                <th className="px-5 py-3 font-bold">DATA</th>
                <th className="px-5 py-3 font-bold">VPN</th>
                <th className="px-5 py-3 font-bold">RISK</th>
                <th className="px-5 py-3 font-bold">LAST SEEN</th>
              </tr>
            </thead>
            <tbody>
              {systems.map((s) => (
                <tr key={s.host} className="border-b border-border last:border-0 hover:bg-muted/30 cursor-pointer">
                  <td className="px-5 py-3 text-xs font-bold">{s.host}</td>
                  <td className="px-5 py-3 text-xs">{s.user}</td>
                  <td className="px-5 py-3"><StatusDot status={s.status} /></td>
                  <td className="px-5 py-3 min-w-[120px]"><div className="flex items-center gap-2"><span className="text-xs w-8">{s.cpu}%</span><Meter value={s.cpu} accent={s.cpu > 80} /></div></td>
                  <td className="px-5 py-3 min-w-[120px]"><div className="flex items-center gap-2"><span className="text-xs w-8">{s.ram}%</span><Meter value={s.ram} accent={s.ram > 80} /></div></td>
                  <td className="px-5 py-3 text-xs">{s.net}</td>
                  <td className="px-5 py-3 text-xs">{s.data}</td>
                  <td className="px-5 py-3 text-xs">{s.vpn ? <span className="text-critical font-bold">ACTIVE</span> : <span className="text-muted-foreground">—</span>}</td>
                  <td className="px-5 py-3"><SeverityBadge s={s.risk} /></td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">{s.seen}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
