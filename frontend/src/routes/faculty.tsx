import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SectionCard, KpiGrid, StatusDot, SeverityBadge } from "@/components/AppShell";

export const Route = createFileRoute("/faculty")({
  head: () => ({ meta: [{ title: "SecureSOC — Faculty Dashboard" }] }),
  component: FacultyPage,
});

const students = Array.from({ length: 12 }, (_, i) => ({
  name: ["A. Sharma","R. Iyer","M. Khan","P. Das","S. Nair","T. Rao","K. Menon","J. Verma","D. Bose","N. Kaur","V. Reddy","H. Ali"][i],
  roll: `CSE-2026-${String(101 + i).padStart(3,"0")}`,
  host: `LAB-PC-${String(i+1).padStart(2,"0")}`,
  app: ["chrome.exe","Code.exe","Word.exe","Excel.exe","PyCharm","Terminal","Edge","Zoom"][i%8],
  status: (i%7===0?"idle":i%9===0?"offline":"exam") as "online"|"offline"|"idle"|"exam",
  risk: (i%6===0?"HIGH":i%3===0?"MEDIUM":"LOW") as "HIGH"|"MEDIUM"|"LOW",
  idle: `${(i*3)%20}m`,
}));

function FacultyPage() {
  return (
    <AppShell title="Faculty Dashboard" subtitle="CLASSROOM SUPERVISION">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l: "STUDENTS PRESENT", v: 38, p: true },
          { l: "IN EXAM MODE", v: 32, p: true },
          { l: "SUSPICIOUS ACTIVITY", v: 3, d: true },
          { l: "ATTENDANCE", v: "97%" },
        ]}/>

        <div className="grid grid-cols-4 gap-4">
          {students.map((s, i) => (
            <div key={i} className="bg-card border border-border rounded-lg p-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-primary text-primary-foreground grid place-items-center font-bold text-sm">
                  {s.name.split(" ").map(p=>p[0]).join("")}
                </div>
                <div className="min-w-0">
                  <div className="text-sm font-bold truncate">{s.name}</div>
                  <div className="text-[10px] text-muted-foreground tracking-widest">{s.roll}</div>
                </div>
              </div>
              <div className="mt-4 space-y-2 text-[11px]">
                <Row k="HOST" v={s.host}/>
                <Row k="ACTIVE APP" v={s.app}/>
                <Row k="IDLE" v={s.idle}/>
              </div>
              <div className="mt-4 flex items-center justify-between">
                <StatusDot status={s.status}/>
                <SeverityBadge s={s.risk}/>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-6">
          <SectionCard title="Recent Suspicious Activity">
            <table className="w-full text-sm">
              <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border">
                <th className="px-2 pb-2 font-bold">TIME</th><th className="px-2 pb-2 font-bold">STUDENT</th><th className="px-2 pb-2 font-bold">EVENT</th><th className="px-2 pb-2 font-bold">SEVERITY</th>
              </tr></thead>
              <tbody>
                {[
                  {t:"14:02", n:"A. Sharma", e:"Attempted app switch during exam (Alt+Tab ×4)", s:"HIGH" as const},
                  {t:"13:44", n:"M. Khan", e:"USB inserted — not whitelisted", s:"CRITICAL" as const},
                  {t:"13:12", n:"D. Bose", e:"Browser navigated to blocked domain", s:"HIGH" as const},
                ].map((r,i)=>(
                  <tr key={i} className="border-b border-border last:border-0">
                    <td className="px-2 py-3 text-xs">{r.t}</td>
                    <td className="px-2 py-3 text-xs font-bold">{r.n}</td>
                    <td className="px-2 py-3 text-xs">{r.e}</td>
                    <td className="px-2 py-3"><SeverityBadge s={r.s}/></td>
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

function Row({k,v}:{k:string;v:string}){
  return <div className="flex justify-between"><span className="text-muted-foreground tracking-widest">{k}</span><span className="font-bold truncate max-w-32">{v}</span></div>;
}
