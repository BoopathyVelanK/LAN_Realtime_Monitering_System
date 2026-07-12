import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SectionCard, Meter } from "@/components/AppShell";
import { BarChart, Bar, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Tooltip, Cell } from "recharts";

export const Route = createFileRoute("/internet-usage")({
  head: () => ({ meta: [{ title: "SecureSOC — Internet Usage" }] }),
  component: InternetUsage,
});

const domains = [
  { d: "google.com", v: 4820 },
  { d: "github.com", v: 3210 },
  { d: "stackoverflow.com", v: 2110 },
  { d: "youtube.com", v: 1980 },
  { d: "wikipedia.org", v: 940 },
  { d: "chatgpt.com", v: 720 },
  { d: "cdn.jsdelivr.net", v: 510 },
];

const consumers = Array.from({length:8},(_,i)=>({
  host:`LAB-PC-${String(i+1).padStart(2,"0")}`,
  student:["A. Sharma","R. Iyer","M. Khan","P. Das","S. Nair","T. Rao","K. Menon","J. Verma"][i],
  mb: 480 - i * 42,
  pct: 92 - i * 8,
}));

function InternetUsage() {
  return (
    <AppShell title="Internet Usage" subtitle="EGRESS ANALYTICS">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l:"DOWNLOAD TODAY", v:"312 GB", p:true },
          { l:"UPLOAD TODAY", v:"48 GB" },
          { l:"BLOCKED REQUESTS", v: 214, d:true },
          { l:"PEAK BANDWIDTH", v:"184 MB/s" },
        ]}/>

        <div className="grid grid-cols-12 gap-5">
          <SectionCard title="Top Domains (MB)" className="col-span-8">
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={domains} layout="vertical">
                  <CartesianGrid stroke="var(--border)" strokeDasharray="2 4"/>
                  <XAxis type="number" stroke="var(--muted-foreground)" fontSize={10}/>
                  <YAxis type="category" dataKey="d" stroke="var(--muted-foreground)" fontSize={10} width={140}/>
                  <Tooltip contentStyle={{background:"var(--card)",border:"1px solid var(--border)",fontSize:12}}/>
                  <Bar dataKey="v" radius={[0,3,3,0]}>
                    {domains.map((_,i)=><Cell key={i} fill={i===0?"var(--primary)":"var(--muted-foreground)"}/>)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </SectionCard>

          <SectionCard title="Top Consumers" className="col-span-4">
            <div className="space-y-3">
              {consumers.map((c,i)=>(
                <div key={i}>
                  <div className="flex justify-between text-[11px] font-bold mb-1">
                    <span>{c.host} <span className="text-muted-foreground font-normal">· {c.student}</span></span>
                    <span>{c.mb} MB</span>
                  </div>
                  <Meter value={c.pct} accent={c.pct > 85}/>
                </div>
              ))}
            </div>
          </SectionCard>
        </div>
      </div>
    </AppShell>
  );
}
