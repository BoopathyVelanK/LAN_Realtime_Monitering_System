import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SeverityBadge } from "@/components/AppShell";
import { Upload, Download, Plus, Play, Pencil, Trash2 } from "lucide-react";

export const Route = createFileRoute("/detection-rules")({
  head: () => ({ meta: [{ title: "SecureSOC — Detection Rules" }] }),
  component: DetectionRules,
});

const rules = [
  { id: "RULE-001", name: "Unauthorized USB Insertion", cat: "USB", sev: "CRITICAL" as const, hits: 42, enabled: true, src: "Built-in" },
  { id: "RULE-002", name: "VPN Client Process (WireGuard/OpenVPN)", cat: "Network", sev: "HIGH" as const, hits: 18, enabled: true, src: "Sigma" },
  { id: "RULE-003", name: "Idle > 30 minutes during exam", cat: "Idle", sev: "HIGH" as const, hits: 7, enabled: true, src: "Built-in" },
  { id: "RULE-004", name: "Blacklisted application: uTorrent", cat: "Application", sev: "CRITICAL" as const, hits: 3, enabled: true, src: "Custom" },
  { id: "RULE-005", name: "Excessive Upload > 250MB / 10m", cat: "Bandwidth", sev: "HIGH" as const, hits: 12, enabled: true, src: "Custom" },
  { id: "RULE-006", name: "Lateral movement — SMB scan", cat: "Network", sev: "CRITICAL" as const, hits: 1, enabled: true, src: "Sigma" },
  { id: "RULE-007", name: "Suspicious PowerShell base64", cat: "Process", sev: "HIGH" as const, hits: 4, enabled: false, src: "Sigma" },
  { id: "RULE-008", name: "Multiple failed passkey attempts", cat: "Auth", sev: "MEDIUM" as const, hits: 9, enabled: true, src: "Built-in" },
];

function DetectionRules() {
  return (
    <AppShell title="Detection Rules" subtitle="RULE ENGINE">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l: "TOTAL RULES", v: 42 },
          { l: "ENABLED", v: 38, p: true },
          { l: "TRIGGERED TODAY", v: 96 },
          { l: "SIGMA RULES", v: 21 },
        ]}/>

        <div className="flex flex-wrap gap-2 mb-4">
          <button className="inline-flex items-center gap-1.5 bg-primary text-primary-foreground rounded px-3 py-2 text-[10px] font-bold tracking-widest"><Plus className="w-3 h-3"/>NEW RULE</button>
          <button className="inline-flex items-center gap-1.5 border border-border rounded px-3 py-2 text-[10px] font-bold tracking-widest hover:bg-muted"><Upload className="w-3 h-3"/>IMPORT SIGMA YAML</button>
          <button className="inline-flex items-center gap-1.5 border border-border rounded px-3 py-2 text-[10px] font-bold tracking-widest hover:bg-muted"><Download className="w-3 h-3"/>EXPORT</button>
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
              {["RULE ID","NAME","CATEGORY","SEVERITY","HITS 24H","SOURCE","STATE","ACTIONS"].map(c=><th key={c} className="px-4 py-3 font-bold">{c}</th>)}
            </tr></thead>
            <tbody>
              {rules.map(r=>(
                <tr key={r.id} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 text-xs font-bold">{r.id}</td>
                  <td className="px-4 py-3 text-xs">{r.name}</td>
                  <td className="px-4 py-3 text-xs">{r.cat}</td>
                  <td className="px-4 py-3"><SeverityBadge s={r.sev}/></td>
                  <td className="px-4 py-3 text-xs font-bold">{r.hits}</td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground">{r.src}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-flex items-center gap-1.5 text-[10px] font-bold tracking-widest ${r.enabled?"text-primary":"text-muted-foreground"}`}>
                      <span className={`w-2 h-2 rounded-full ${r.enabled?"bg-primary":"bg-muted-foreground"}`}/>
                      {r.enabled?"ENABLED":"DISABLED"}
                    </span>
                  </td>
                  <td className="px-4 py-3 flex gap-1.5">
                    <button className="border border-border p-1.5 rounded hover:bg-muted"><Play className="w-3.5 h-3.5"/></button>
                    <button className="border border-border p-1.5 rounded hover:bg-muted"><Pencil className="w-3.5 h-3.5"/></button>
                    <button className="border border-border p-1.5 rounded hover:bg-muted text-critical"><Trash2 className="w-3.5 h-3.5"/></button>
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
