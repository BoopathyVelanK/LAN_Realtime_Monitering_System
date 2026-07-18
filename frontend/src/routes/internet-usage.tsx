import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SectionCard, Meter } from "@/components/AppShell";
import { BarChart, Bar, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Tooltip, Cell } from "recharts";
import { useInternetUsageEvents } from "@/api/queries";

export const Route = createFileRoute("/internet-usage")({
  head: () => ({ meta: [{ title: "SecureSOC — Internet Usage" }] }),
  component: InternetUsage,
});

// "Top Domains" stays fully mock below - the agent has no proxy/DNS
// logging (collector.py's NetworkUsageTracker only sees byte counters,
// never which domain/host traffic went to), so there is no data source
// for a per-domain breakdown to ever wire up without adding an entirely
// new agent capability. KPI cards also stay mock - "today" is a rolling
// aggregate the raw paginated feed can't honestly compute without
// fetching everything, and BLOCKED REQUESTS has no backend concept at
// all (no firewall/proxy block logging).
const domains = [
  { d: "google.com", v: 4820 },
  { d: "github.com", v: 3210 },
  { d: "stackoverflow.com", v: 2110 },
  { d: "youtube.com", v: 1980 },
  { d: "wikipedia.org", v: 940 },
  { d: "chatgpt.com", v: 720 },
  { d: "cdn.jsdelivr.net", v: 510 },
];

function InternetUsage() {
  // "Top Consumers" IS real: aggregated per-hostname totals from the raw
  // recent-sample feed (real hostname, real summed MB). The mock's
  // per-student names are dropped - InternetUsageEvent has no student/
  // user association, only endpoint/hostname.
  const { data, isLoading, isError } = useInternetUsageEvents({ size: 100 });
  const events = data?.content ?? [];

  const byHost = new Map<string, { mb: number }>();
  for (const e of events) {
    const entry = byHost.get(e.hostname) ?? { mb: 0 };
    entry.mb += e.uploadMb + e.downloadMb;
    byHost.set(e.hostname, entry);
  }
  const consumers = [...byHost.entries()]
    .map(([host, v]) => ({ host, mb: Math.round(v.mb * 10) / 10 }))
    .sort((a, b) => b.mb - a.mb)
    .slice(0, 8);
  const maxMb = consumers[0]?.mb || 1;

  return (
    <AppShell title="Internet Usage" subtitle="EGRESS ANALYTICS">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l:"DOWNLOAD TODAY (MOCK)", v:"312 GB", p:true },
          { l:"UPLOAD TODAY (MOCK)", v:"48 GB" },
          { l:"BLOCKED REQUESTS (MOCK)", v: 214, d:true },
          { l:"PEAK BANDWIDTH (MOCK)", v:"184 MB/s" },
        ]}/>

        <div className="grid grid-cols-12 gap-5">
          <SectionCard title="Top Domains (MB) — illustrative, no proxy logging exists" className="col-span-8">
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

          <SectionCard title="Top Consumers (recent samples)" className="col-span-4">
            {isLoading && <div className="text-xs text-muted-foreground">Loading…</div>}
            {isError && <div className="text-xs text-critical">Could not load internet-usage events.</div>}
            {!isLoading && !isError && consumers.length === 0 && (
              <div className="text-xs text-muted-foreground">No internet-usage events recorded yet.</div>
            )}
            <div className="space-y-3">
              {consumers.map((c) => (
                <div key={c.host}>
                  <div className="flex justify-between text-[11px] font-bold mb-1">
                    <span>{c.host}</span>
                    <span>{c.mb} MB</span>
                  </div>
                  <Meter value={(c.mb / maxMb) * 100} accent={c.mb / maxMb > 0.85} />
                </div>
              ))}
            </div>
          </SectionCard>
        </div>
      </div>
    </AppShell>
  );
}
