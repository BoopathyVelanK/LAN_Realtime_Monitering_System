import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SectionCard, Meter } from "@/components/AppShell";
import { BarChart, Bar, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Tooltip } from "recharts";

export const Route = createFileRoute("/data-usage")({
  head: () => ({ meta: [{ title: "SecureSOC — Data Analytics" }] }),
  component: DataUsage,
});

const daily = Array.from({ length: 14 }, (_, i) => ({ d: `D${i+1}`, gb: Math.round(80 + Math.random() * 120) }));
const top = [
  { host: "LAB-PC-09", gb: 41.2 },
  { host: "LAB-PC-04", gb: 28.7 },
  { host: "LAB-PC-17", gb: 18.4 },
  { host: "FAC-DC-MAIN", gb: 12.1 },
  { host: "LAB-PC-22", gb: 8.9 },
];

function DataUsage() {
  return (
    <AppShell title="Data Usage Analytics" subtitle="CONSUMPTION TRENDS">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[{l:"TODAY",v:"412 GB"},{l:"THIS WEEK",v:"2.4 TB"},{l:"THIS MONTH",v:"9.8 TB"},{l:"OVER QUOTA",v:"4",d:true}].map((c,i)=>(
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.d?"text-critical":""}`}>{c.v}</div>
            </div>
          ))}
        </div>

        <div className="grid grid-cols-12 gap-5">
          <SectionCard title="Daily Consumption (last 14 days, GB)" className="col-span-8">
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={daily}>
                  <CartesianGrid stroke="var(--border)" strokeDasharray="2 4"/>
                  <XAxis dataKey="d" stroke="var(--muted-foreground)" fontSize={10}/>
                  <YAxis stroke="var(--muted-foreground)" fontSize={10}/>
                  <Tooltip contentStyle={{background:"var(--card)",border:"1px solid var(--border)",fontSize:12}}/>
                  <Bar dataKey="gb" fill="var(--primary)" radius={[3,3,0,0]}/>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </SectionCard>
          <SectionCard title="Top Data Consumers" className="col-span-4">
            <div className="space-y-4">
              {top.map((r) => {
                const pct = (r.gb / top[0].gb) * 100;
                return (
                  <div key={r.host}>
                    <div className="flex justify-between text-[11px] font-bold tracking-wider mb-1.5">
                      <span>{r.host}</span><span>{r.gb} GB</span>
                    </div>
                    <Meter value={pct} accent={pct > 80} />
                  </div>
                );
              })}
            </div>
          </SectionCard>
        </div>
      </div>
    </AppShell>
  );
}
