import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SeverityBadge } from "@/components/AppShell";

export const Route = createFileRoute("/vpn-monitoring")({
  head: () => ({ meta: [{ title: "SecureSOC — VPN Monitoring" }] }),
  component: VPNPage,
});

const rows = [
  { host: "LAB-PC-04", vpn: "WireGuard", status: "ACTIVE", detected: "13:20:12", risk: "MEDIUM" as const },
  { host: "LAB-PC-09", vpn: "OpenVPN", status: "ACTIVE", detected: "12:58:01", risk: "HIGH" as const },
  { host: "LAB-PC-17", vpn: "NordVPN", status: "INSTALLED", detected: "10:11:42", risk: "MEDIUM" as const },
  { host: "FAC-DC-MAIN", vpn: "Cisco AnyConnect", status: "ACTIVE", detected: "08:42:00", risk: "LOW" as const },
];

function VPNPage() {
  return (
    <AppShell title="VPN Monitoring" subtitle="HEURISTIC DETECTION">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[{l:"VPN PROCESSES",v:"4",d:true},{l:"VPN SERVICES",v:"7"},{l:"VPN ADAPTERS",v:"5"},{l:"HOSTS WITH VPN",v:"4",d:true}].map((c,i)=>(
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.d?"text-critical":""}`}>{c.v}</div>
            </div>
          ))}
        </div>
        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
              <th className="px-5 py-3 font-bold">HOST</th><th className="px-5 py-3 font-bold">VPN CLIENT</th><th className="px-5 py-3 font-bold">STATUS</th><th className="px-5 py-3 font-bold">DETECTED</th><th className="px-5 py-3 font-bold">RISK</th>
            </tr></thead>
            <tbody>
              {rows.map((r,i)=>(
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs font-bold">{r.host}</td>
                  <td className="px-5 py-3 text-xs">{r.vpn}</td>
                  <td className="px-5 py-3"><span className={`px-2.5 py-1 text-[10px] font-bold tracking-wider rounded ${r.status==="ACTIVE"?"bg-critical text-critical-foreground":"border border-border"}`}>{r.status}</span></td>
                  <td className="px-5 py-3 text-xs">{r.detected}</td>
                  <td className="px-5 py-3"><SeverityBadge s={r.risk} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
