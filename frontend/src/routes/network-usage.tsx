import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SectionCard } from "@/components/AppShell";
import { AreaChart, Area, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Tooltip } from "recharts";

export const Route = createFileRoute("/network-usage")({
  head: () => ({ meta: [{ title: "SecureSOC — Network Usage" }] }),
  component: NetworkUsage,
});

const series = Array.from({ length: 30 }, (_, i) => ({
  t: `${i}m`,
  up: Math.round(40 + Math.sin(i / 3) * 30 + Math.random() * 20),
  down: Math.round(120 + Math.cos(i / 2) * 50 + Math.random() * 30),
}));

function NetworkUsage() {
  return (
    <AppShell title="Network Usage" subtitle="BANDWIDTH TELEMETRY">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[{l:"UPLOAD NOW",v:"42 MB/s"},{l:"DOWNLOAD NOW",v:"184 MB/s"},{l:"PEAK 24H",v:"912 MB/s"},{l:"BANDWIDTH SPIKES",v:"3",d:true}].map((c,i)=>(
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.d?"text-critical":""}`}>{c.v}</div>
            </div>
          ))}
        </div>
        <SectionCard title="LAN Bandwidth (Live)">
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={series}>
                <defs>
                  <linearGradient id="up2" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--primary)" stopOpacity={0.5}/><stop offset="100%" stopColor="var(--primary)" stopOpacity={0}/></linearGradient>
                  <linearGradient id="dn2" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--critical)" stopOpacity={0.4}/><stop offset="100%" stopColor="var(--critical)" stopOpacity={0}/></linearGradient>
                </defs>
                <CartesianGrid stroke="var(--border)" strokeDasharray="2 4"/>
                <XAxis dataKey="t" stroke="var(--muted-foreground)" fontSize={10}/>
                <YAxis stroke="var(--muted-foreground)" fontSize={10}/>
                <Tooltip contentStyle={{background:"var(--card)",border:"1px solid var(--border)",fontSize:12}}/>
                <Area type="monotone" dataKey="down" stroke="var(--critical)" fill="url(#dn2)" strokeWidth={2}/>
                <Area type="monotone" dataKey="up" stroke="var(--primary)" fill="url(#up2)" strokeWidth={2}/>
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </SectionCard>
      </div>
    </AppShell>
  );
}
