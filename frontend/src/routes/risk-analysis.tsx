import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SectionCard } from "@/components/AppShell";
import { RadialBarChart, RadialBar, ResponsiveContainer, PolarAngleAxis } from "recharts";
import { useEndpoints, useRiskScores } from "@/api/queries";
import { Loader2 } from "lucide-react";

export const Route = createFileRoute("/risk-analysis")({
  head: () => ({ meta: [{ title: "SecureSOC — Risk Analysis" }] }),
  component: RiskAnalysis,
});

function RiskAnalysis() {
  const { data: endpoints, isLoading: endpointsLoading } = useEndpoints();
  const { data: riskScores, isLoading: riskLoading } = useRiskScores(endpoints);

  const isLoading = endpointsLoading || riskLoading;
  let maxScore = 0;
  let maxLevel = "SAFE";
  let fill = "var(--muted-foreground)";
  if (riskScores && riskScores.length > 0) {
    const top = [...riskScores].sort((a, b) => b.score - a.score)[0];
    maxScore = top.score;
    maxLevel = top.level;
    if (maxLevel === "CRITICAL") fill = "var(--primary)";
    else if (maxLevel === "HIGH") fill = "var(--critical)";
    else if (maxLevel === "MEDIUM") fill = "#c08a3e";
  }
  const scoreData = [{ name: "score", value: maxScore, fill }];

  return (
    <AppShell title="Risk Analysis" subtitle="RULE-BASED SCORING">
      <div className="px-8 pb-8 grid grid-cols-12 gap-5">
        <SectionCard title="Network Risk Index" className="col-span-4">
          <div className="h-64 relative">
            {isLoading ? (
              <div className="absolute inset-0 flex items-center justify-center text-muted-foreground">
                <Loader2 className="w-6 h-6 animate-spin" />
              </div>
            ) : (
              <>
                <ResponsiveContainer width="100%" height="100%">
                  <RadialBarChart innerRadius="70%" outerRadius="100%" data={scoreData} startAngle={210} endAngle={-30}>
                    <PolarAngleAxis type="number" domain={[0, 100]} tick={false} />
                    <RadialBar background={{ fill: "var(--muted)" }} dataKey="value" cornerRadius={8} />
                  </RadialBarChart>
                </ResponsiveContainer>
                <div className="absolute inset-0 flex flex-col items-center justify-center">
                  <div className="text-5xl font-bold" style={{ color: fill }}>{maxScore}</div>
                  <div className="text-[10px] tracking-widest text-muted-foreground font-bold mt-1">{maxLevel} RISK</div>
                </div>
              </>
            )}
          </div>
        </SectionCard>
        <SectionCard title="Active Risk Rules" className="col-span-8">
          <table className="w-full text-sm -mx-1">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border">
              <th className="px-2 pb-2 font-bold">RULE</th><th className="px-2 pb-2 font-bold">WEIGHT</th><th className="px-2 pb-2 font-bold">HITS (24H)</th><th className="px-2 pb-2 font-bold">LEVEL</th>
            </tr></thead>
            <tbody>
              <tr>
                <td colSpan={4} className="px-2 py-8 text-center text-xs text-muted-foreground">
                  Backend does not expose per-rule hit metrics yet.
                </td>
              </tr>
            </tbody>
          </table>
        </SectionCard>
      </div>
    </AppShell>
  );
}
