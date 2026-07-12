import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SectionCard, Meter } from "@/components/AppShell";
import { Download, Upload, HardDrive, CheckCircle2 } from "lucide-react";

export const Route = createFileRoute("/backup")({
  head: () => ({ meta: [{ title: "SecureSOC — Backup & Restore" }] }),
  component: BackupPage,
});

const backups = [
  { id:"BK-2026-01-10-0300", when:"2026-01-10 03:00", size:"4.2 GB", type:"Scheduled", ok:true },
  { id:"BK-2026-01-09-0300", when:"2026-01-09 03:00", size:"4.1 GB", type:"Scheduled", ok:true },
  { id:"BK-2026-01-08-1512", when:"2026-01-08 15:12", size:"4.1 GB", type:"Manual", ok:true },
  { id:"BK-2026-01-08-0300", when:"2026-01-08 03:00", size:"4.0 GB", type:"Scheduled", ok:true },
  { id:"BK-2026-01-07-0300", when:"2026-01-07 03:00", size:"4.0 GB", type:"Scheduled", ok:false },
];

function BackupPage() {
  return (
    <AppShell title="Backup & Restore" subtitle="DATA PROTECTION">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l:"LAST BACKUP", v:"03:00 today", p:true },
          { l:"NEXT SCHEDULED", v:"03:00 tomorrow" },
          { l:"RETAINED SNAPSHOTS", v: 42 },
          { l:"STORAGE USED", v:"186 / 500 GB" },
        ]}/>

        <div className="grid grid-cols-12 gap-5">
          <SectionCard title="Storage" className="col-span-4">
            <div className="flex items-center gap-3">
              <HardDrive className="w-8 h-8 text-primary"/>
              <div>
                <div className="text-2xl font-bold">186 GB</div>
                <div className="text-[11px] text-muted-foreground tracking-widest">USED OF 500 GB</div>
              </div>
            </div>
            <div className="mt-4"><Meter value={37}/></div>
            <div className="mt-6 space-y-2">
              <button className="w-full flex items-center justify-center gap-2 bg-primary text-primary-foreground rounded px-3 py-2.5 text-[11px] font-bold tracking-widest"><Download className="w-3.5 h-3.5"/>BACKUP NOW</button>
              <button className="w-full flex items-center justify-center gap-2 border border-border rounded px-3 py-2.5 text-[11px] font-bold tracking-widest hover:bg-muted"><Upload className="w-3.5 h-3.5"/>RESTORE FROM FILE</button>
            </div>
          </SectionCard>

          <SectionCard title="Backup History" className="col-span-8">
            <table className="w-full text-sm">
              <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border">
                {["SNAPSHOT ID","TIMESTAMP","SIZE","TYPE","VERIFICATION","ACTIONS"].map(c=><th key={c} className="px-2 pb-2 font-bold">{c}</th>)}
              </tr></thead>
              <tbody>
                {backups.map(b=>(
                  <tr key={b.id} className="border-b border-border last:border-0">
                    <td className="px-2 py-3 text-xs font-mono">{b.id}</td>
                    <td className="px-2 py-3 text-xs">{b.when}</td>
                    <td className="px-2 py-3 text-xs">{b.size}</td>
                    <td className="px-2 py-3 text-xs">{b.type}</td>
                    <td className="px-2 py-3">
                      {b.ok
                        ? <span className="inline-flex items-center gap-1.5 text-[10px] font-bold tracking-widest text-primary"><CheckCircle2 className="w-3.5 h-3.5"/>VERIFIED</span>
                        : <span className="text-[10px] font-bold tracking-widest text-critical">FAILED CHECKSUM</span>}
                    </td>
                    <td className="px-2 py-3 flex gap-1.5">
                      <button className="text-[10px] font-bold tracking-widest border border-border px-2 py-1 rounded hover:bg-muted">RESTORE</button>
                      <button className="text-[10px] font-bold tracking-widest border border-border px-2 py-1 rounded hover:bg-muted">DOWNLOAD</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </SectionCard>
        </div>
      </div>
    </AppShell>
  );
}
