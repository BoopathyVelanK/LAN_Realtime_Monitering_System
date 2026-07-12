import { createFileRoute } from "@tanstack/react-router";
import { AppShell, Meter } from "@/components/AppShell";
import { Search, AppWindow } from "lucide-react";

export const Route = createFileRoute("/running-applications")({
  head: () => ({ meta: [{ title: "SecureSOC — Applications" }] }),
  component: RunningApps,
});

const apps = [
  { name: "chrome.exe", pid: 4128, cpu: 12.4, mem: 412, status: "Running", host: "LAB-PC-01", user: "student_01", t: "00:42:11" },
  { name: "code.exe", pid: 5022, cpu: 8.1, mem: 388, status: "Running", host: "LAB-PC-04", user: "student_07", t: "01:08:23" },
  { name: "python.exe", pid: 6711, cpu: 41.2, mem: 612, status: "Running", host: "LAB-PC-09", user: "student_88", t: "00:14:02" },
  { name: "wireguard.exe", pid: 1188, cpu: 0.4, mem: 22, status: "Running", host: "LAB-PC-04", user: "student_07", t: "00:09:51" },
  { name: "AnyDesk.exe", pid: 2002, cpu: 1.8, mem: 88, status: "BLACKLISTED", host: "LAB-PC-17", user: "student_42", t: "00:02:11" },
  { name: "explorer.exe", pid: 1024, cpu: 0.9, mem: 64, status: "Running", host: "LAB-PC-22", user: "student_11", t: "02:14:00" },
  { name: "uTorrent.exe", pid: 9912, cpu: 22.1, mem: 244, status: "BLACKLISTED", host: "LAB-PC-09", user: "student_88", t: "00:38:14" },
  { name: "vscode-server", pid: 3344, cpu: 6.7, mem: 312, status: "Running", host: "FAC-DC-MAIN", user: "admin_root", t: "08:01:42" },
];

function RunningApps() {
  return (
    <AppShell title="Running Applications" subtitle="PROCESS TELEMETRY">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[
            { l: "TRACKED PROCESSES", v: "2,418" },
            { l: "UNIQUE BINARIES", v: "184" },
            { l: "BLACKLISTED ACTIVE", v: "2", danger: true },
            { l: "AVG CPU LOAD", v: "11.4%" },
          ].map((c, i) => (
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.danger ? "text-critical" : ""}`}>{c.v}</div>
            </div>
          ))}
        </div>

        <div className="bg-card border border-border rounded-lg p-4 flex items-center gap-4 mb-5">
          <div className="flex items-center gap-2 border border-border rounded px-3 py-1.5 bg-background flex-1 max-w-md">
            <Search className="w-3.5 h-3.5 text-muted-foreground" />
            <input className="bg-transparent outline-none text-xs flex-1" placeholder="Search by application, host or user" />
          </div>
          <select className="border border-border rounded px-3 py-1.5 text-xs font-bold bg-background"><option>ALL HOSTS</option></select>
          <select className="border border-border rounded px-3 py-1.5 text-xs font-bold bg-background"><option>ALL STATUS</option><option>BLACKLISTED</option></select>
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
                <th className="px-5 py-3 font-bold">APPLICATION</th>
                <th className="px-5 py-3 font-bold">PID</th>
                <th className="px-5 py-3 font-bold">CPU</th>
                <th className="px-5 py-3 font-bold">MEMORY</th>
                <th className="px-5 py-3 font-bold">RUNTIME</th>
                <th className="px-5 py-3 font-bold">HOST</th>
                <th className="px-5 py-3 font-bold">USER</th>
                <th className="px-5 py-3 font-bold">STATUS</th>
              </tr>
            </thead>
            <tbody>
              {apps.map((a, i) => (
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs font-bold flex items-center gap-2"><AppWindow className="w-3.5 h-3.5 text-muted-foreground" />{a.name}</td>
                  <td className="px-5 py-3 text-xs">{a.pid}</td>
                  <td className="px-5 py-3 min-w-[120px]"><div className="flex items-center gap-2"><span className="text-xs w-12">{a.cpu}%</span><Meter value={a.cpu * 2} accent={a.cpu > 30} /></div></td>
                  <td className="px-5 py-3 text-xs">{a.mem} MB</td>
                  <td className="px-5 py-3 text-xs">{a.t}</td>
                  <td className="px-5 py-3 text-xs font-bold">{a.host}</td>
                  <td className="px-5 py-3 text-xs">{a.user}</td>
                  <td className="px-5 py-3">
                    {a.status === "BLACKLISTED"
                      ? <span className="px-2.5 py-1 text-[10px] font-bold tracking-wider rounded bg-critical text-critical-foreground">BLACKLISTED</span>
                      : <span className="px-2.5 py-1 text-[10px] font-bold tracking-wider rounded border border-border text-muted-foreground">RUNNING</span>}
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
