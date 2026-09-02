import { createFileRoute } from "@tanstack/react-router";
import { AppShell, StatCard, SectionCard, SeverityBadge, Meter } from "@/components/AppShell";
import { TrendingUp, AlertTriangle, Download, Clock, ShieldAlert, Wifi } from "lucide-react";
import {
  PieChart, Pie, Cell, Legend, ResponsiveContainer
} from "recharts";
import { useEndpoints, useAlerts, useRiskScores } from "@/api/queries";

export const Route = createFileRoute("/")({
  head: () => ({ meta: [{ title: "SecureSOC — Dashboard" }] }),
  component: Dashboard,
});

function Dashboard() {
  const { data: endpoints, isLoading: endpointsLoading } = useEndpoints();
  const { data: alerts, isLoading: alertsLoading } = useAlerts();
  const { data: riskScores, isLoading: riskLoading } = useRiskScores(endpoints);

  const total = endpoints?.length ?? 0;
  const online = endpoints?.filter((e) => e.status === "ONLINE").length ?? 0;
  const offline = total - online;

  const criticalAlerts = alerts?.filter((a) => a.severity === "CRITICAL" && a.status === "OPEN").length ?? 0;

  let highRiskCount = 0;
  const riskPie = [
    { name: "Low", value: 0, color: "var(--muted-foreground)" },
    { name: "Medium", value: 0, color: "#c08a3e" },
    { name: "High", value: 0, color: "var(--critical)" },
    { name: "Critical", value: 0, color: "var(--primary)" },
  ];
  let totalRisk = 0;
  if (riskScores) {
    for (const r of riskScores) {
      totalRisk++;
      if (r.level === "CRITICAL") riskPie[3].value++;
      else if (r.level === "HIGH") riskPie[2].value++;
      else if (r.level === "MEDIUM") riskPie[1].value++;
      else riskPie[0].value++;
      if (r.level === "HIGH" || r.level === "CRITICAL") highRiskCount++;
    }
  }

  return (
    <AppShell title="Operations Overview" subtitle="DASHBOARD">
      <div className="p-6 grid grid-cols-12 gap-5">
        {/* Hero KPIs */}
        <div className="col-span-4 bg-card border border-border rounded-lg p-6">
          <div className="text-[11px] tracking-widest text-muted-foreground font-bold">TOTAL ENDPOINTS</div>
          <div className="text-5xl font-bold mt-4 tracking-tight">{endpointsLoading ? "—" : total}</div>
          <div className="flex items-center gap-2 mt-6 text-xs">
            <TrendingUp className="w-4 h-4 text-primary" />
            <span className="font-bold text-primary">{endpointsLoading ? "—" : online} ONLINE</span>
            <span className="text-muted-foreground">· {endpointsLoading ? "—" : offline} OFFLINE</span>
          </div>
        </div>

        <div className="col-span-4 bg-primary text-primary-foreground rounded-lg p-6 flex flex-col">
          <div className="text-[11px] tracking-widest opacity-80 font-bold">CRITICAL ALERTS</div>
          <div className="text-5xl font-bold mt-4">{alertsLoading ? "—" : String(criticalAlerts).padStart(2, "0")}</div>
          <button className="mt-auto pt-6 w-full">
            <div className="bg-black/20 hover:bg-black/30 transition-colors rounded px-4 py-3 flex items-center justify-between text-xs font-bold tracking-wider">
              ACTION REQUIRED
              <AlertTriangle className="w-4 h-4" />
            </div>
          </button>
        </div>

        <div className="col-span-4 grid grid-cols-2 gap-3">
          <StatCard label="ACTIVE USERS" value="—" />
          <StatCard label="HIGH RISK" value={riskLoading ? "—" : String(highRiskCount)} accent="danger" />
          <StatCard label="IDLE SYSTEMS" value="—" accent="primary" />
          <StatCard label="LAN DATA / DAY" value="—" />
        </div>

        {/* Network usage chart - see file header comment. */}
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
            <div className="flex h-full items-center justify-center text-xs text-muted-foreground">Historical network telemetry unavailable</div>
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
                <div className="text-2xl font-bold">{riskLoading ? "—" : totalRisk}</div>
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
                <th className="px-2 pb-2 font-bold">EVENT</th>
                <th className="px-2 pb-2 font-bold">SEVERITY</th>
              </tr>
            </thead>
            <tbody>
              {alertsLoading && (
                <tr><td colSpan={4} className="px-2 py-8 text-center text-xs text-muted-foreground">Loading recent events…</td></tr>
              )}
              {!alertsLoading && alerts?.length === 0 && (
                <tr><td colSpan={4} className="px-2 py-8 text-center text-xs text-muted-foreground">No recent events.</td></tr>
              )}
              {!alertsLoading && alerts?.slice(0, 5).map((e) => (
                <tr key={e.id} className="border-b border-border last:border-0">
                  <td className="px-2 py-3 text-xs">{new Date(e.createdAt).toLocaleTimeString()}</td>
                  <td className="px-2 py-3 text-xs font-bold">{e.hostname}</td>
                  <td className="px-2 py-3 text-xs">{e.title}</td>
                  <td className="px-2 py-3"><SeverityBadge s={e.severity as "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO"} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </SectionCard>

        {/* System health */}
        <SectionCard title="System Health" className="col-span-4">
          <div className="space-y-4">
            <Health label="CPU CORE LOAD" right="—" value={0} />
            <Health label="MEMORY UTILIZATION" right="—" value={0} />
            <Health label="STORAGE" right="—" value={0} />
            <Health
              label="LAN HEARTBEAT OK"
              right={endpointsLoading ? "—" : `${online}/${total}`}
              value={total > 0 ? Math.round((online / total) * 100) : 100}
            />
          </div>
        </SectionCard>

        {/* CPU & Memory Trend */}
        <SectionCard title="CPU & Memory Trend (1h)" className="col-span-8">
          <div className="h-52">
            <div className="flex h-full items-center justify-center text-xs text-muted-foreground">Historical CPU & memory telemetry unavailable</div>
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
