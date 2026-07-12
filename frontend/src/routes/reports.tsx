import { createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";
import { FileText, FileSpreadsheet, FileCode2, Download } from "lucide-react";

export const Route = createFileRoute("/reports")({
  head: () => ({ meta: [{ title: "SecureSOC — Reports" }] }),
  component: ReportsPage,
});

const reports = [
  "Daily Login Report", "Running Applications Report", "Idle Report",
  "USB Report", "VPN Report", "Risk Report",
  "System Health Report", "Data Usage Report", "Offline Activity Report",
];

function ReportsPage() {
  return (
    <AppShell title="Reports" subtitle="EXPORT & ARCHIVE">
      <div className="px-8 pb-8 grid grid-cols-3 gap-5">
        {reports.map((r) => (
          <div key={r} className="bg-card border border-border rounded-lg p-6 flex flex-col">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-10 h-10 rounded bg-primary/10 border border-primary/30 flex items-center justify-center text-primary">
                <FileText className="w-5 h-5" />
              </div>
              <div className="font-bold text-sm">{r}</div>
            </div>
            <div className="text-[11px] text-muted-foreground mb-5">Generated nightly · 2025-10-24</div>
            <div className="mt-auto flex gap-2">
              <button className="flex-1 flex items-center justify-center gap-1.5 text-[10px] font-bold tracking-wider bg-primary text-primary-foreground px-3 py-2 rounded"><Download className="w-3 h-3" /> PDF</button>
              <button className="flex-1 flex items-center justify-center gap-1.5 text-[10px] font-bold tracking-wider border border-border px-3 py-2 rounded hover:bg-muted"><FileSpreadsheet className="w-3 h-3" /> XLSX</button>
              <button className="flex-1 flex items-center justify-center gap-1.5 text-[10px] font-bold tracking-wider border border-border px-3 py-2 rounded hover:bg-muted"><FileCode2 className="w-3 h-3" /> CSV</button>
            </div>
          </div>
        ))}
      </div>
    </AppShell>
  );
}
