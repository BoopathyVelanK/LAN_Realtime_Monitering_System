import { createFileRoute, Link } from "@tanstack/react-router";
import { AppShell, SectionCard, StatusDot, SeverityBadge, Meter, KpiGrid } from "@/components/AppShell";
import { AreaChart, Area, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Tooltip } from "recharts";
import { ArrowLeft, Cpu, HardDrive, MemoryStick, Wifi, Usb, ShieldOff } from "lucide-react";

export const Route = createFileRoute("/endpoints/$id")({
  head: ({ params }) => ({ meta: [{ title: `SecureSOC — ${params.id}` }] }),
  component: EndpointDetail,
});

const cpuSeries = Array.from({ length: 30 }, (_, i) => ({
  t: `${i}m`,
  cpu: 20 + Math.round(Math.sin(i / 3) * 15 + Math.random() * 25),
  ram: 40 + Math.round(Math.cos(i / 4) * 10 + Math.random() * 15),
}));

const processes = [
  { name: "chrome.exe", pid: 4128, cpu: "18.2%", mem: "812 MB", user: "student_20", risk: "LOW" as const },
  { name: "Code.exe", pid: 7842, cpu: "9.4%", mem: "612 MB", user: "student_20", risk: "LOW" as const },
  { name: "WireGuard.exe", pid: 9012, cpu: "1.2%", mem: "42 MB", user: "student_20", risk: "HIGH" as const },
  { name: "uTorrent.exe", pid: 5233, cpu: "22.8%", mem: "180 MB", user: "student_20", risk: "CRITICAL" as const },
  { name: "python.exe", pid: 3311, cpu: "5.1%", mem: "220 MB", user: "student_20", risk: "LOW" as const },
];

const timeline = [
  { t: "14:02:11", e: "USB inserted: SanDisk Cruzer 16GB — POLICY VIOLATION", s: "CRITICAL" as const },
  { t: "13:58:44", e: "Idle → active (returned to keyboard)", s: "INFO" as const },
  { t: "13:20:12", e: "VPN process detected: WireGuard.exe", s: "MEDIUM" as const },
  { t: "13:11:02", e: "Excessive upload — 412 MB in 10 minutes", s: "HIGH" as const },
  { t: "12:00:00", e: "User login: student_20", s: "INFO" as const },
];

function EndpointDetail() {
  const { id } = Route.useParams();
  return (
    <AppShell title={id} subtitle="ENDPOINT DETAIL">
      <div className="px-8 pb-8">
        <Link to="/endpoints" className="inline-flex items-center gap-1.5 text-[11px] font-bold tracking-widest text-muted-foreground hover:text-foreground mb-4">
          <ArrowLeft className="w-3.5 h-3.5" /> BACK TO ENDPOINTS
        </Link>

        <div className="grid grid-cols-12 gap-5 mb-5">
          <div className="col-span-8 bg-card border border-border rounded-lg p-6">
            <div className="flex items-start justify-between">
              <div>
                <div className="text-[10px] tracking-widest text-muted-foreground font-bold">SYSTEM OVERVIEW</div>
                <div className="text-2xl font-bold mt-2" style={{ fontFamily: "Georgia, serif" }}>{id}</div>
                <div className="text-xs text-muted-foreground mt-1">Windows 11 Pro 23H2 · Intel i7-12700 · 16 GB DDR4 · 512 GB NVMe</div>
              </div>
              <StatusDot status="online" />
            </div>
            <div className="grid grid-cols-4 gap-6 mt-6">
              <Metric icon={<Cpu className="w-4 h-4" />} label="CPU" value="42%" />
              <Metric icon={<MemoryStick className="w-4 h-4" />} label="RAM" value="9.2 / 16 GB" />
              <Metric icon={<HardDrive className="w-4 h-4" />} label="DISK" value="384 / 512 GB" />
              <Metric icon={<Wifi className="w-4 h-4" />} label="NET" value="184 MB/s" />
            </div>
          </div>
          <div className="col-span-4 bg-primary text-primary-foreground rounded-lg p-6">
            <div className="text-[10px] tracking-widest opacity-80 font-bold">RISK SCORE</div>
            <div className="text-6xl font-bold mt-3">78</div>
            <div className="text-xs opacity-80 mt-2">HIGH — 3 open alerts, blacklisted app detected</div>
            <button className="mt-6 w-full bg-black/20 hover:bg-black/30 py-2 rounded text-[11px] font-bold tracking-widest">ISOLATE ENDPOINT</button>
          </div>
        </div>

        <KpiGrid cards={[
          { l: "LOGGED-IN USER", v: "student_20" },
          { l: "SESSION UPTIME", v: "02h 11m" },
          { l: "IDLE TIME", v: "3m 22s" },
          { l: "ACTIVE WINDOW", v: "chrome.exe" },
        ]} />

        <div className="grid grid-cols-12 gap-5">
          <SectionCard title="CPU & Memory (30m)" className="col-span-8">
            <div className="h-56">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={cpuSeries}>
                  <defs>
                    <linearGradient id="cpu-d" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--primary)" stopOpacity={0.4}/><stop offset="100%" stopColor="var(--primary)" stopOpacity={0}/></linearGradient>
                    <linearGradient id="ram-d" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--critical)" stopOpacity={0.3}/><stop offset="100%" stopColor="var(--critical)" stopOpacity={0}/></linearGradient>
                  </defs>
                  <CartesianGrid stroke="var(--border)" strokeDasharray="2 4"/>
                  <XAxis dataKey="t" stroke="var(--muted-foreground)" fontSize={10}/>
                  <YAxis stroke="var(--muted-foreground)" fontSize={10}/>
                  <Tooltip contentStyle={{background:"var(--card)",border:"1px solid var(--border)",fontSize:12}}/>
                  <Area type="monotone" dataKey="cpu" stroke="var(--primary)" fill="url(#cpu-d)" strokeWidth={2}/>
                  <Area type="monotone" dataKey="ram" stroke="var(--critical)" fill="url(#ram-d)" strokeWidth={2}/>
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </SectionCard>

          <SectionCard title="Peripherals & Network" className="col-span-4">
            <div className="space-y-3">
              <PeriRow icon={<Usb className="w-4 h-4"/>} label="USB devices" v="1 unauthorized" alert />
              <PeriRow icon={<ShieldOff className="w-4 h-4"/>} label="VPN client" v="WireGuard ACTIVE" alert />
              <PeriRow icon={<Wifi className="w-4 h-4"/>} label="LAN link" v="1 Gbps · full-duplex" />
              <PeriRow icon={<HardDrive className="w-4 h-4"/>} label="Disk health" v="SMART OK" />
              <div className="pt-2">
                <div className="text-[10px] font-bold tracking-widest mb-2">DISK USAGE</div>
                <Meter value={75} />
                <div className="text-[10px] text-muted-foreground mt-1">384 GB / 512 GB (75%)</div>
              </div>
            </div>
          </SectionCard>

          <SectionCard title="Running Processes" className="col-span-8">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border">
                  {["PROCESS", "PID", "CPU", "MEMORY", "USER", "RISK", ""].map(c => <th key={c} className="pb-2 px-2 font-bold">{c}</th>)}
                </tr>
              </thead>
              <tbody>
                {processes.map((p, i) => (
                  <tr key={i} className="border-b border-border last:border-0">
                    <td className="px-2 py-3 text-xs font-bold">{p.name}</td>
                    <td className="px-2 py-3 text-xs">{p.pid}</td>
                    <td className="px-2 py-3 text-xs">{p.cpu}</td>
                    <td className="px-2 py-3 text-xs">{p.mem}</td>
                    <td className="px-2 py-3 text-xs">{p.user}</td>
                    <td className="px-2 py-3"><SeverityBadge s={p.risk}/></td>
                    <td className="px-2 py-3 flex gap-1.5">
                      <button className="text-[10px] font-bold tracking-widest border border-border px-2 py-1 rounded hover:bg-muted">ALLOW</button>
                      <button className="text-[10px] font-bold tracking-widest bg-critical text-critical-foreground px-2 py-1 rounded">TERMINATE</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </SectionCard>

          <SectionCard title="Event Timeline" className="col-span-4">
            <ul className="space-y-3">
              {timeline.map((e, i) => (
                <li key={i} className="border-l-2 border-primary pl-3 py-1">
                  <div className="text-[10px] text-muted-foreground font-bold tracking-widest">{e.t}</div>
                  <div className="text-xs mt-1">{e.e}</div>
                  <div className="mt-1.5"><SeverityBadge s={e.s}/></div>
                </li>
              ))}
            </ul>
          </SectionCard>
        </div>
      </div>
    </AppShell>
  );
}

function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div>
      <div className="flex items-center gap-2 text-[10px] text-muted-foreground tracking-widest font-bold">{icon}{label}</div>
      <div className="text-xl font-bold mt-2">{value}</div>
    </div>
  );
}

function PeriRow({ icon, label, v, alert }: { icon: React.ReactNode; label: string; v: string; alert?: boolean }) {
  return (
    <div className="flex items-center justify-between text-xs">
      <div className="flex items-center gap-2">{icon}<span>{label}</span></div>
      <span className={`font-bold ${alert ? "text-critical" : ""}`}>{v}</span>
    </div>
  );
}
