import { createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";
import { useIdleEvents } from "@/api/queries";

export const Route = createFileRoute("/idle-monitoring")({
  head: () => ({ meta: [{ title: "SecureSOC — Idle Monitoring" }] }),
  component: IdleMonitoring,
});

type Band = "ACTIVE" | "IDLE" | "LONG IDLE" | "CRITICAL";

const colors: Record<Band, string> = {
  ACTIVE: "bg-green-600",
  IDLE: "bg-yellow-500",
  "LONG IDLE": "bg-orange-500",
  CRITICAL: "bg-critical",
};

// Same thresholds as the classification bar below - a real function of
// the real idleSeconds value from IdleEventResponse, not mock data.
function classify(idleSeconds: number): Band {
  const minutes = idleSeconds / 60;
  if (minutes < 5) return "ACTIVE";
  if (minutes < 15) return "IDLE";
  if (minutes < 30) return "LONG IDLE";
  return "CRITICAL";
}

function formatDuration(seconds: number): string {
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ${seconds % 60}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}

// USER column and the ACTION button's target have no backing data - the
// backend doesn't associate idle samples with an OS username (that's
// only tracked on login/logout events, which don't cross-reference idle
// events at the DB level). This is a raw chronological idle-sample feed
// (newest first) rather than one row per endpoint's *current* status -
// the backend has no "latest idle sample per endpoint" endpoint, only
// the raw event list, so the same host can appear more than once here.
function IdleMonitoring() {
  const { data, isLoading, isError } = useIdleEvents({ size: 50 });
  const rows = data?.content ?? [];

  const counts = rows.reduce(
    (acc, r) => {
      acc[classify(r.idleSeconds)]++;
      return acc;
    },
    { ACTIVE: 0, IDLE: 0, "LONG IDLE": 0, CRITICAL: 0 } as Record<Band, number>,
  );

  return (
    <AppShell title="Idle Monitoring" subtitle="ACTIVITY DETECTION">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {(Object.keys(colors) as Band[]).map((band) => (
            <div key={band} className="bg-card border border-border rounded-lg p-5">
              <div className="flex items-center gap-2 text-[10px] tracking-widest text-muted-foreground font-bold">
                <span className={`w-2 h-2 rounded-full ${colors[band]}`} /> {band} (loaded)
              </div>
              <div className="text-3xl font-bold mt-3">{counts[band]}</div>
            </div>
          ))}
        </div>

        <div className="bg-card border border-border rounded-lg p-5 mb-5">
          <div className="text-[11px] tracking-widest text-muted-foreground font-bold mb-3">CLASSIFICATION THRESHOLDS</div>
          <div className="flex h-3 rounded-full overflow-hidden border border-border">
            <div className="bg-green-600 w-[16%]" title="0-5m Active" />
            <div className="bg-yellow-500 w-[34%]" title="5-15m Idle" />
            <div className="bg-orange-500 w-[34%]" title="15-30m Long Idle" />
            <div className="bg-critical flex-1" title=">30m Critical" />
          </div>
          <div className="flex justify-between text-[10px] tracking-wider text-muted-foreground mt-2 font-bold">
            <span>0m</span><span>5m</span><span>15m</span><span>30m</span><span>60m+</span>
          </div>
        </div>

        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
                <th className="px-5 py-3 font-bold">HOSTNAME</th>
                <th className="px-5 py-3 font-bold">USER</th>
                <th className="px-5 py-3 font-bold">IDLE DURATION</th>
                <th className="px-5 py-3 font-bold">RECORDED</th>
                <th className="px-5 py-3 font-bold">CLASSIFICATION</th>
                <th className="px-5 py-3 font-bold">ACTION</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && (
                <tr><td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">Loading idle events…</td></tr>
              )}
              {isError && (
                <tr><td colSpan={6} className="px-5 py-8 text-center text-xs text-critical">Could not load idle events.</td></tr>
              )}
              {!isLoading && !isError && rows.length === 0 && (
                <tr><td colSpan={6} className="px-5 py-8 text-center text-xs text-muted-foreground">No idle events recorded yet.</td></tr>
              )}
              {rows.map((r) => {
                const band = classify(r.idleSeconds);
                return (
                  <tr key={r.id} className="border-b border-border last:border-0">
                    <td className="px-5 py-3 text-xs font-bold">{r.hostname}</td>
                    <td className="px-5 py-3 text-xs text-muted-foreground">—</td>
                    <td className="px-5 py-3 text-xs">{formatDuration(r.idleSeconds)}</td>
                    <td className="px-5 py-3 text-xs text-muted-foreground">{new Date(r.recordedAt).toLocaleString()}</td>
                    <td className="px-5 py-3">
                      <span className="inline-flex items-center gap-2 text-[10px] font-bold tracking-wider">
                        <span className={`w-2 h-2 rounded-full ${colors[band]}`} /> {band}
                      </span>
                    </td>
                    <td className="px-5 py-3">
                      <button className="text-[10px] font-bold tracking-wider text-primary hover:underline">NOTIFY FACULTY</button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
