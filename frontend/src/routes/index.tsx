import { createFileRoute } from "@tanstack/react-router";
import { AppShell, StatCard, SectionCard, SeverityBadge, Meter } from "@/components/AppShell";
import { TrendingUp, AlertTriangle, Download, Clock, ShieldAlert, Wifi } from "lucide-react";
import {
  AreaChart, Area, ResponsiveContainer, Tooltip, XAxis, YAxis, CartesianGrid,
  BarChart, Bar, PieChart, Pie, Cell, Legend,
} from "recharts";

export const Route = createFileRoute("/")({
  head: () => ({ meta: [{ title: "SecureSOC — Dashboard" }] }),
  component: Dashboard,
});

const networkSeries = Array.from({ length: 24 }, (_, i) => ({
  t: `${String(i).padStart(2, "0")}:00`,
  up: Math.round(80 + Math.sin(i / 2) * 40 + Math.random() * 30),
  down: Math.round(160 + Math.cos(i / 3) * 60 + Math.random() * 40),
}));

const cpuSeries = Array.from({ length: 12 }, (_, i) => ({
  t: `${i * 5}m`,
  cpu: Math.round(30 + Math.random() * 50),
  ram: Math.round(40 + Math.random() * 35),
}));

const riskPie = [
  { name: "Low", value: 243, color: "var(--muted-foreground)" },
  { name: "Medium", value: 87, color: "#c08a3e" },
  { name: "High", value: 12, color: "var(--critical)" },
  { name: "Critical", value: 4, color: "var(--primary)" },
];

const recentEvents = [
  { ts: "14:02:11", host: "LAB-PC-17", user: "student_42", type: "Unauthorized USB Inserted", sev: "CRITICAL" as const },
  { ts: "13:58:44", host: "LAB-PC-22", user: "student_11", type: "Idle > 30m (Critical Idle)", sev: "HIGH" as const },
  { ts: "13:45:01", host: "FAC-DC-MAIN", user: "admin_root", type: "Lateral Movement Indicator", sev: "CRITICAL" as const },
  { ts: "13:20:12", host: "LAB-PC-04", user: "student_07", type: "VPN Process Detected (WireGuard)", sev: "MEDIUM" as const },
  { ts: "13:11:02", host: "LAB-PC-09", user: "student_88", type: "Excessive Data Upload (412 MB)", sev: "HIGH" as const },
];

function Dashboard() {
  return (
    <AppShell title="Operations Overview" subtitle="DASHBOARD">
      <div className="p-6 grid grid-cols-12 gap-5">
        {/* Hero KPIs */}
        <div className="col-span-4 bg-card border border-border rounded-lg p-6">
          <div className="text-[11px] tracking-widest text-muted-foreground font-bold">TOTAL ENDPOINTS</div>
          <div className="text-5xl font-bold mt-4 tracking-tight">142</div>
          <div className="flex items-center gap-2 mt-6 text-xs">
            <TrendingUp className="w-4 h-4 text-primary" />
            <span className="font-bold text-primary">128 ONLINE</span>
            <span className="text-muted-foreground">· 14 OFFLINE</span>
          </div>
        </div>

        <div className="col-span-4 bg-primary text-primary-foreground rounded-lg p-6 flex flex-col">
          <div className="text-[11px] tracking-widest opacity-80 font-bold">CRITICAL ALERTS</div>
          <div className="text-5xl font-bold mt-4">04</div>
          <button className="mt-auto pt-6 w-full">
            <div className="bg-black/20 hover:bg-black/30 transition-colors rounded px-4 py-3 flex items-center justify-between text-xs font-bold tracking-wider">
              ACTION REQUIRED
              <AlertTriangle className="w-4 h-4" />
            </div>
          </button>
        </div>

        <div className="col-span-4 grid grid-cols-2 gap-3">
          <StatCard label="ACTIVE USERS" value="118" />
          <StatCard label="HIGH RISK" value="12" accent="danger" />
          <StatCard label="IDLE SYSTEMS" value="23" accent="primary" />
          <StatCard label="LAN DATA / DAY" value="2.4 TB" />
        </div>

        {/* Network usage chart */}
        <SectionCard
          title="Network Usage (24h, MB/s)"
          className="col-span-8"
          action={
            <div className="flex gap-2">
              <button className="px-3 py-1.5 border border-border rounded text-[10px] font-bold tracking-wider hover:bg-muted">24H</button>
              <button className="px-3 py-1.5 border border-border rounded text-[10px] font-bold tracking-wider hover:bg-muted">7D</button>
              <button className="px-3 py-1.5 bg-primary text-primary-foreground rounded text-[10px] font-bold tracking-wider">EXPORT</button>
            </div>
          }
        >
          <div className="h-60">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={networkSeries}>
                <defs>
                  <linearGradient id="up" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--primary)" stopOpacity={0.4} />
                    <stop offset="100%" stopColor="var(--primary)" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="down" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--critical)" stopOpacity={0.3} />
                    <stop offset="100%" stopColor="var(--critical)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="var(--border)" strokeDasharray="2 4" />
                <XAxis dataKey="t" stroke="var(--muted-foreground)" fontSize={10} />
                <YAxis stroke="var(--muted-foreground)" fontSize={10} />
                <Tooltip contentStyle={{ background: "var(--card)", border: "1px solid var(--border)", fontSize: 12, fontFamily: "inherit" }} />
                <Area type="monotone" dataKey="down" stroke="var(--critical)" fill="url(#down)" strokeWidth={2} />
                <Area type="monotone" dataKey="up" stroke="var(--primary)" fill="url(#up)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </SectionCard>

        {/* Risk distribution */}
        <SectionCard title="Risk Distribution" className="col-span-4">
          <div className="h-60 relative">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={riskPie} dataKey="value" innerRadius={55} outerRadius={85} paddingAngle={2}>
                  {riskPie.map((d, i) => <Cell key={i} fill={d.color} />)}
                </Pie>
                <Legend iconType="square" wrapperStyle={{ fontSize: 11, fontWeight: 700, letterSpacing: 1 }} />
              </PieChart>
            </ResponsiveContainer>
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none -mt-6">
              <div className="text-center">
                <div className="text-2xl font-bold">346</div>
                <div className="text-[10px] tracking-widest text-muted-foreground">TOTAL</div>
              </div>
            </div>
          </div>
        </SectionCard>

        {/* Recent Events */}
        <SectionCard
          title="Recent Events"
          className="col-span-8"
          action={
            <div className="flex gap-2">
              <button className="px-3 py-1.5 border border-border rounded text-[10px] font-bold tracking-wider hover:bg-muted">REFRESH</button>
              <button className="px-3 py-1.5 bg-primary text-primary-foreground rounded text-[10px] font-bold tracking-wider">EXPORT</button>
            </div>
          }
        >
          <table className="w-full text-sm -mx-1">
            <thead>
              <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border">
                <th className="px-2 pb-2 font-bold">TIME</th>
                <th className="px-2 pb-2 font-bold">HOST</th>
                <th className="px-2 pb-2 font-bold">USER</th>
                <th className="px-2 pb-2 font-bold">EVENT</th>
                <th className="px-2 pb-2 font-bold">SEVERITY</th>
              </tr>
            </thead>
            <tbody>
              {recentEvents.map((e, i) => (
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-2 py-3 text-xs">{e.ts}</td>
                  <td className="px-2 py-3 text-xs font-bold">{e.host}</td>
                  <td className="px-2 py-3 text-xs text-muted-foreground">{e.user}</td>
                  <td className="px-2 py-3 text-xs">{e.type}</td>
                  <td className="px-2 py-3"><SeverityBadge s={e.sev} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </SectionCard>

        {/* System health */}
        <SectionCard title="System Health" className="col-span-4">
          <div className="space-y-4">
            <Health label="CPU CORE LOAD" right="42%" value={42} />
            <Health label="MEMORY UTILIZATION" right="12.4 GB" value={55} />
            <Health label="STORAGE" right="92% CRITICAL" value={92} accent />
            <Health label="LAN HEARTBEAT OK" right="142/142" value={100} />
          </div>
        </SectionCard>

        {/* CPU / RAM trend */}
        <SectionCard title="CPU & Memory Trend (1h)" className="col-span-8">
          <div className="h-52">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={cpuSeries}>
                <CartesianGrid stroke="var(--border)" strokeDasharray="2 4" />
                <XAxis dataKey="t" stroke="var(--muted-foreground)" fontSize={10} />
                <YAxis stroke="var(--muted-foreground)" fontSize={10} />
                <Tooltip contentStyle={{ background: "var(--card)", border: "1px solid var(--border)", fontSize: 12 }} />
                <Bar dataKey="cpu" fill="var(--primary)" radius={[3, 3, 0, 0]} />
                <Bar dataKey="ram" fill="var(--critical)" opacity={0.6} radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </SectionCard>

        <div className="col-span-4 grid grid-cols-2 gap-3">
          <ActionTile icon={<Download className="w-5 h-5" />} label="EXPORT REPORT" />
          <ActionTile icon={<Clock className="w-5 h-5" />} label="TIMELINE" />
          <ActionTile icon={<ShieldAlert className="w-5 h-5" />} label="EXAM MODE" />
          <ActionTile icon={<Wifi className="w-5 h-5" />} label="LAN SYNC" />
        </div>
      </div>
    </AppShell>
  );
}

function Health({ label, right, value, accent }: { label: string; right: string; value: number; accent?: boolean }) {
  return (
    <div>
      <div className="flex justify-between text-[11px] font-bold tracking-wider mb-2">
        <span>{label}</span>
        <span className={accent ? "text-critical" : ""}>{right}</span>
      </div>
      <Meter value={value} accent={accent} />
    </div>
  );
}

function ActionTile({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <button className="bg-card border border-border rounded-lg p-5 flex flex-col items-center gap-3 hover:bg-muted transition-colors">
      <div className="text-primary">{icon}</div>
      <div className="text-[10px] font-bold tracking-widest text-center">{label}</div>
    </button>
  );
}
