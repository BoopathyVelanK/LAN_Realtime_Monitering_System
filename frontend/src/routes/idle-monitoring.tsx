import { createFileRoute } from "@tanstack/react-router";
import { AppShell } from "@/components/AppShell";

export const Route = createFileRoute("/idle-monitoring")({
  head: () => ({ meta: [{ title: "SecureSOC — Idle Monitoring" }] }),
  component: IdleMonitoring,
});

type IdleRow = { host: string; user: string; duration: string; minutes: number; last: string; band: "ACTIVE" | "IDLE" | "LONG IDLE" | "CRITICAL" };

const rows: IdleRow[] = [
  { host: "LAB-PC-01", user: "student_01", duration: "0m 32s", minutes: 0, last: "now", band: "ACTIVE" },
  { host: "LAB-PC-04", user: "student_07", duration: "2m 11s", minutes: 2, last: "2m ago", band: "ACTIVE" },
  { host: "LAB-PC-12", user: "—",          duration: "12m 04s", minutes: 12, last: "12m ago", band: "IDLE" },
  { host: "LAB-PC-22", user: "student_11", duration: "34m 19s", minutes: 34, last: "34m ago", band: "CRITICAL" },
  { host: "LAB-PC-29", user: "student_45", duration: "8m 51s", minutes: 8, last: "8m ago", band: "IDLE" },
  { host: "LAB-PC-31", user: "—",          duration: "1h 02m", minutes: 62, last: "1h ago", band: "CRITICAL" },
  { host: "LAB-PC-33", user: "student_19", duration: "21m 03s", minutes: 21, last: "21m ago", band: "LONG IDLE" },
  { host: "LAB-PC-44", user: "student_22", duration: "4m 11s", minutes: 4, last: "4m ago", band: "ACTIVE" },
];

const colors: Record<IdleRow["band"], string> = {
  ACTIVE: "bg-green-600",
  IDLE: "bg-yellow-500",
  "LONG IDLE": "bg-orange-500",
  CRITICAL: "bg-critical",
};

function IdleMonitoring() {
  return (
    <AppShell title="Idle Monitoring" subtitle="ACTIVITY DETECTION">
      <div className="px-8 pb-8">
        <div className="grid grid-cols-4 gap-4 mb-5">
          {[
            { l: "ACTIVE", v: 87, c: "bg-green-600" },
            { l: "IDLE", v: 18, c: "bg-yellow-500" },
            { l: "LONG IDLE", v: 9, c: "bg-orange-500" },
            { l: "CRITICAL IDLE", v: 4, c: "bg-critical" },
          ].map((c, i) => (
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="flex items-center gap-2 text-[10px] tracking-widest text-muted-foreground font-bold">
                <span className={`w-2 h-2 rounded-full ${c.c}`} /> {c.l}
              </div>
              <div className="text-3xl font-bold mt-3">{c.v}</div>
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
                <th className="px-5 py-3 font-bold">LAST ACTIVITY</th>
                <th className="px-5 py-3 font-bold">CLASSIFICATION</th>
                <th className="px-5 py-3 font-bold">ACTION</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.host} className="border-b border-border last:border-0">
                  <td className="px-5 py-3 text-xs font-bold">{r.host}</td>
                  <td className="px-5 py-3 text-xs">{r.user}</td>
                  <td className="px-5 py-3 text-xs">{r.duration}</td>
                  <td className="px-5 py-3 text-xs text-muted-foreground">{r.last}</td>
                  <td className="px-5 py-3">
                    <span className="inline-flex items-center gap-2 text-[10px] font-bold tracking-wider">
                      <span className={`w-2 h-2 rounded-full ${colors[r.band]}`} /> {r.band}
                    </span>
                  </td>
                  <td className="px-5 py-3">
                    <button className="text-[10px] font-bold tracking-wider text-primary hover:underline">NOTIFY FACULTY</button>
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
