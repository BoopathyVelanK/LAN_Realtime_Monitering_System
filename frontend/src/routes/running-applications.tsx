import { createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";
import { Search, AppWindow } from "lucide-react";
import { useRunningAppSnapshots } from "@/api/queries";

export const Route = createFileRoute("/running-applications")({
  head: () => ({ meta: [{ title: "SecureSOC — Applications" }] }),
  component: RunningApps,
});

// CPU/MEMORY/RUNTIME/USER/STATUS columns have no backing data -
// collector.py's get_running_applications() only ever sends processName,
// windowTitle, and pid per process (see its docstring: per-process CPU/
// memory stats, OS username attribution, and any blacklist concept are
// all future work, not something the agent captures today). Search/
// filter controls are left as non-functional UI, same reason.
function RunningApps() {
  const { data, isLoading, isError } = useRunningAppSnapshots({ size: 20 });
  const snapshots = data?.content ?? [];

  const rows = snapshots.flatMap((s) =>
    s.apps.map((a, i) => ({
      key: `${s.id}-${i}`,
      name: a.processName ?? "(unknown)",
      pid: a.pid,
      host: s.hostname,
      capturedAt: s.capturedAt,
    })),
  );

  const uniqueBinaries = new Set(rows.map((r) => r.name)).size;

  return (
    <AppShell title="Running Applications" subtitle="PROCESS TELEMETRY">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[
            { l: "PROCESSES LOADED", v: rows.length },
            { l: "UNIQUE BINARIES", v: uniqueBinaries },
            { l: "BLACKLISTED ACTIVE (MOCK)", v: "2", danger: true },
            { l: "AVG CPU LOAD (MOCK)", v: "11.4%" },
          ].map((c, i) => (
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
              <div className={`text-3xl font-bold mt-3 ${c.danger ? "text-critical" : ""}`}>{c.v}</div>
            </div>
          ))}
        </div>

        <div className="bg-card border border-border rounded-lg p-4 flex items-center gap-4 mb-5">
          <div className="flex items-center gap-2 border border-border rounded px-3 py-1.5 bg-background flex-1 max-w-md">
            <Search className="w-3.5 h-3.5 text-muted-foreground" />
            <input className="bg-transparent outline-none text-xs flex-1" placeholder="Search by application, host or user" disabled />
          </div>
          <select className="border border-border rounded px-3 py-1.5 text-xs font-bold bg-background" disabled><option>ALL HOSTS</option></select>
          <select className="border border-border rounded px-3 py-1.5 text-xs font-bold bg-background" disabled><option>ALL STATUS</option></select>
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
                <th className="px-5 py-3 font-bold">APPLICATION</th>
                <th className="px-5 py-3 font-bold">PID</th>
                <th className="px-5 py-3 font-bold">CPU</th>
                <th className="px-5 py-3 font-bold">MEMORY</th>
                <th className="px-5 py-3 font-bold">CAPTURED</th>
                <th className="px-5 py-3 font-bold">HOST</th>
                <th className="px-5 py-3 font-bold">USER</th>
                <th className="px-5 py-3 font-bold">STATUS</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr><td colSpan={8} className="px-5 py-8 text-center text-xs text-muted-foreground">Loading process snapshots…</td></tr>
              )}
              {isError && (
                <tr><td colSpan={8} className="px-5 py-8 text-center text-xs text-critical">Could not load process snapshots.</td></tr>
              )}
              {!isLoading && !isError && rows.length === 0 && (
                <tr><td colSpan={8} className="px-5 py-8 text-center text-xs text-muted-foreground">No process snapshots recorded yet.</td></tr>
              )}
              {rows.map((a) => (
                <tr key={a.key} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs font-bold flex items-center gap-2"><AppWindow className="w-3.5 h-3.5 text-muted-foreground" />{a.name}</td>
                  <td className="px-5 py-3 text-xs">{a.pid ?? "—"}</td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-5 py-3 text-xs">{new Date(a.capturedAt).toLocaleString()}</td>
                  <td className="px-5 py-3 text-xs font-bold">{a.host}</td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">—</td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">—</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
