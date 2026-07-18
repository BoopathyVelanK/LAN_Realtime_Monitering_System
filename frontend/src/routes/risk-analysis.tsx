import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SectionCard, SeverityBadge } from "@/components/AppShell";
import { RadialBarChart, RadialBar, ResponsiveContainer, PolarAngleAxis } from "recharts";

export const Route = createFileRoute("/risk-analysis")({
  head: () => ({ meta: [{ title: "SecureSOC — Risk Analysis" }] }),
  component: RiskAnalysis,
});

// MOCK DATA (Phase 4A audit): no detection-rules or risk-scoring backend
// exists yet (Phase 4 detection engine - no RiskController, no rule
// engine, no Sigma/threshold rule storage). Note this page's data model
// (per-rule weight/hit-count, one aggregate "network risk index") is also
// a different shape than RiskScoreResponse (per-endpoint score/level) in
// types/api.ts - even once a risk backend exists, this page may need a
// dedicated rules-summary endpoint rather than useRiskScores(). Left
// as-is rather than force-fitting the wrong hook onto the wrong UI.
const rules = [
  { rule: "Unauthorized USB inserted", weight: 30, hits: 4, level: "CRITICAL" as const },
  { rule: "VPN active during exam window", weight: 25, hits: 2, level: "HIGH" as const },
  { rule: "Idle > 30 minutes", weight: 10, hits: 9, level: "MEDIUM" as const },
  { rule: "Blacklisted application running", weight: 35, hits: 2, level: "CRITICAL" as const },
  { rule: "Excessive upload (>250MB)", weight: 20, hits: 5, level: "HIGH" as const },
  { rule: "Offline > 1h (no LAN sync)", weight: 15, hits: 3, level: "MEDIUM" as const },
];

const score = [{ name: "score", value: 78, fill: "var(--critical)" }];

function RiskAnalysis() {
  return (
    <AppShell title="Risk Analysis" subtitle="RULE-BASED SCORING">
      <div className="px-8 pb-8 grid grid-cols-12 gap-5">
        <SectionCard title="Network Risk Index" className="col-span-4">
          <div className="h-64 relative">
            <ResponsiveContainer width="100%" height="100%">
              <RadialBarChart innerRadius="70%" outerRadius="100%" data={score} startAngle={210} endAngle={-30}>
                <PolarAngleAxis type="number" domain={[0, 100]} tick={false} />
                <RadialBar background={{ fill: "var(--muted)" }} dataKey="value" cornerRadius={8} />
              </RadialBarChart>
            </ResponsiveContainer>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <div className="text-5xl font-bold text-critical">78</div>
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold mt-1">HIGH RISK</div>
            </div>
          </div>
        </SectionCard>
        <SectionCard title="Active Risk Rules" className="col-span-8">
          <table className="w-full text-sm -mx-1">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border">
              <th className="px-2 pb-2 font-bold">RULE</th><th className="px-2 pb-2 font-bold">WEIGHT</th><th className="px-2 pb-2 font-bold">HITS (24H)</th><th className="px-2 pb-2 font-bold">LEVEL</th>
            </tr></thead>
            <tbody>
              {rules.map((r,i)=>(
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-2 py-3 text-xs">{r.rule}</td>
                  <td className="px-2 py-3 text-xs font-bold">{r.weight}</td>
                  <td className="px-2 py-3 text-xs">{r.hits}</td>
                  <td className="px-2 py-3"><SeverityBadge s={r.level} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </SectionCard>
      </div>
    </AppShell>
  );
}
