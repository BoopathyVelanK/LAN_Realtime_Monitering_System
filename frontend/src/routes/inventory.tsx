import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SectionCard } from "@/components/AppShell";
import { useState } from "react";

export const Route = createFileRoute("/inventory")({
  head: () => ({ meta: [{ title: "SecureSOC — Inventory" }] }),
  component: InventoryPage,
});

const hardware = Array.from({length:8},(_,i)=>({
  host:`LAB-PC-${String(i+1).padStart(2,"0")}`,
  cpu:"Intel i7-12700",ram:"16 GB",disk:"512 GB NVMe",gpu:"UHD 770",os:"Win 11 Pro",serial:`SN-${1000+i}`,
}));
const software = [
  {app:"Google Chrome",ver:"131.0",publisher:"Google",installs:142,license:"Free"},
  {app:"Microsoft Office",ver:"2024",publisher:"Microsoft",installs:142,license:"Volume · Perpetual"},
  {app:"Visual Studio Code",ver:"1.94",publisher:"Microsoft",installs:118,license:"Free"},
  {app:"PyCharm Community",ver:"2024.2",publisher:"JetBrains",installs:64,license:"Free"},
  {app:"WireGuard",ver:"0.5.3",publisher:"WireGuard LLC",installs:4,license:"Blocked"},
  {app:"uTorrent",ver:"3.6",publisher:"BitTorrent",installs:1,license:"Blocked"},
];

function InventoryPage() {
  const [tab,setTab] = useState<"hw"|"sw">("hw");
  return (
    <AppShell title="Inventory" subtitle="ASSET & SOFTWARE REGISTRY">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l:"HARDWARE ASSETS", v: 142 },
          { l:"SOFTWARE TITLES", v: 214 },
          { l:"LICENSES IN USE", v: "1,842" },
          { l:"COMPLIANCE", v:"98.6%", p:true },
        ]}/>
        <div className="flex gap-2 mb-4">
          {[["hw","HARDWARE"],["sw","SOFTWARE"]].map(([k,l])=>(
            <button key={k} onClick={()=>setTab(k as any)} className={`px-4 py-2 rounded text-[10px] font-bold tracking-widest ${tab===k?"bg-primary text-primary-foreground":"border border-border hover:bg-muted"}`}>{l}</button>
          ))}
        </div>

        <SectionCard title={tab==="hw"?"Hardware Inventory":"Software Inventory"}>
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border">
              {tab==="hw"
                ? ["HOST","CPU","RAM","DISK","GPU","OS","SERIAL"].map(c=><th key={c} className="px-2 pb-2 font-bold">{c}</th>)
                : ["APPLICATION","VERSION","PUBLISHER","INSTALLS","LICENSE"].map(c=><th key={c} className="px-2 pb-2 font-bold">{c}</th>)}
            </tr></thead>
            <tbody>
              {tab==="hw" ? hardware.map((r,i)=>(
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-2 py-3 text-xs font-bold">{r.host}</td>
                  <td className="px-2 py-3 text-xs">{r.cpu}</td>
                  <td className="px-2 py-3 text-xs">{r.ram}</td>
                  <td className="px-2 py-3 text-xs">{r.disk}</td>
                  <td className="px-2 py-3 text-xs">{r.gpu}</td>
                  <td className="px-2 py-3 text-xs">{r.os}</td>
                  <td className="px-2 py-3 text-[11px] text-muted-foreground">{r.serial}</td>
                </tr>
              )) : software.map((r,i)=>(
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-2 py-3 text-xs font-bold">{r.app}</td>
                  <td className="px-2 py-3 text-xs">{r.ver}</td>
                  <td className="px-2 py-3 text-xs">{r.publisher}</td>
                  <td className="px-2 py-3 text-xs font-bold">{r.installs}</td>
                  <td className="px-2 py-3">
                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold tracking-widest ${r.license==="Blocked"?"bg-critical text-critical-foreground":"border border-border"}`}>{r.license}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </SectionCard>
      </div>
    </AppShell>
  );
}
