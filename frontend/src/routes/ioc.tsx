import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SeverityBadge } from "@/components/AppShell";
import { Upload, Plus, Search } from "lucide-react";

export const Route = createFileRoute("/ioc")({
  head: () => ({ meta: [{ title: "SecureSOC — IOC Management" }] }),
  component: IOCPage,
});

const iocs = [
  { type: "IP", value: "185.220.101.42", sev: "CRITICAL" as const, source: "AbuseIPDB", added: "2025-10-24" },
  { type: "Domain", value: "malicious-cdn.top", sev: "HIGH" as const, source: "OTX", added: "2025-10-23" },
  { type: "URL", value: "http://phish.example/login", sev: "HIGH" as const, source: "PhishTank", added: "2025-10-23" },
  { type: "SHA256", value: "9f86d081884c7d659a2feaa0…", sev: "CRITICAL" as const, source: "VirusTotal", added: "2025-10-22" },
  { type: "Process", value: "mimikatz.exe", sev: "CRITICAL" as const, source: "Internal", added: "2025-10-20" },
  { type: "Process", value: "uTorrent.exe", sev: "HIGH" as const, source: "Internal", added: "2025-10-18" },
  { type: "IP", value: "45.155.205.19", sev: "MEDIUM" as const, source: "AbuseIPDB", added: "2025-10-17" },
];

function IOCPage() {
  return (
    <AppShell title="IOC Management" subtitle="INDICATORS OF COMPROMISE">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l: "TOTAL IOCS", v: 1284 },
          { l: "IP / DOMAIN", v: 812 },
          { l: "FILE HASHES", v: 401 },
          { l: "MATCHES 24H", v: 6, d: true },
        ]}/>

        <div className="flex flex-wrap gap-2 mb-4">
          <div className="flex items-center gap-2 border border-border rounded px-3 py-2 bg-card flex-1 min-w-64">
            <Search className="w-4 h-4 text-muted-foreground"/>
            <input placeholder="Search IOC value, hash, or domain…" className="bg-transparent outline-none text-sm flex-1"/>
          </div>
          <select className="border border-border rounded px-3 py-2 text-xs bg-card">
            <option>All types</option><option>IP</option><option>Domain</option><option>URL</option><option>Hash</option><option>Process</option>
          </select>
          <button className="inline-flex items-center gap-1.5 bg-primary text-primary-foreground rounded px-3 py-2 text-[10px] font-bold tracking-widest"><Plus className="w-3 h-3"/>ADD IOC</button>
          <button className="inline-flex items-center gap-1.5 border border-border rounded px-3 py-2 text-[10px] font-bold tracking-widest hover:bg-muted"><Upload className="w-3 h-3"/>IMPORT CSV</button>
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
              {["TYPE","VALUE","SEVERITY","SOURCE","ADDED","ACTION"].map(c=><th key={c} className="px-4 py-3 font-bold">{c}</th>)}
            </tr></thead>
            <tbody>
              {iocs.map((r,i)=>(
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-4 py-3"><span className="px-2 py-0.5 border border-border rounded text-[10px] font-bold tracking-widest">{r.type}</span></td>
                  <td className="px-4 py-3 text-xs font-mono">{r.value}</td>
                  <td className="px-4 py-3"><SeverityBadge s={r.sev}/></td>
                  <td className="px-4 py-3 text-xs">{r.source}</td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground">{r.added}</td>
                  <td className="px-4 py-3 flex gap-1.5">
                    <button className="text-[10px] font-bold tracking-widest border border-border px-2 py-1 rounded hover:bg-muted">EDIT</button>
                    <button className="text-[10px] font-bold tracking-widest border border-border px-2 py-1 rounded hover:bg-muted text-critical">REMOVE</button>
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
