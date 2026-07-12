import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, StatusDot, SeverityBadge } from "@/components/AppShell";

export const Route = createFileRoute("/student-activity")({
  head: () => ({ meta: [{ title: "SecureSOC — Student Activity" }] }),
  component: StudentActivityPage,
});

const list = Array.from({length:16},(_,i)=>({
  name:["A. Sharma","R. Iyer","M. Khan","P. Das","S. Nair","T. Rao","K. Menon","J. Verma","D. Bose","N. Kaur","V. Reddy","H. Ali","G. Pillai","L. Joshi","S. Kapoor","F. Ansari"][i],
  roll:`CSE-2026-${String(101+i).padStart(3,"0")}`,
  host:`LAB-PC-${String(i+1).padStart(2,"0")}`,
  app:["chrome.exe","Code.exe","Word","Excel","PyCharm","Zoom","Firefox","Edge"][i%8],
  window:["Assignment.docx — Word","index.tsx — VS Code","Google Meet","Chrome — Wikipedia","Terminal","Excel — Marks"][i%6],
  login:`${8+(i%2)}:${String((i*7)%60).padStart(2,"0")} AM`,
  idle:`${(i*3)%30}m`,
  usage:`${(i*84+120)} MB`,
  risk:(i%5===0?"HIGH":i%3===0?"MEDIUM":"LOW") as "HIGH"|"MEDIUM"|"LOW",
  status:(i%9===0?"offline":i%6===0?"idle":"online") as "online"|"offline"|"idle",
}));

function StudentActivityPage() {
  return (
    <AppShell title="Student Activity" subtitle="LIVE LEARNER TELEMETRY">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l:"ACTIVE STUDENTS", v: 118, p:true },
          { l:"AVERAGE IDLE", v:"7m 42s" },
          { l:"TOP APPLICATION", v:"Code.exe" },
          { l:"POLICY VIOLATIONS", v: 3, d:true },
        ]}/>
        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
              {["STUDENT","ROLL","HOST","APP","ACTIVE WINDOW","LOGIN","IDLE","INTERNET","RISK","STATUS"].map(c=><th key={c} className="px-4 py-3 font-bold">{c}</th>)}
            </tr></thead>
            <tbody>
              {list.map((s,i)=>(
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 text-xs font-bold">{s.name}</td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground">{s.roll}</td>
                  <td className="px-4 py-3 text-xs">{s.host}</td>
                  <td className="px-4 py-3 text-xs">{s.app}</td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground truncate max-w-48">{s.window}</td>
                  <td className="px-4 py-3 text-xs">{s.login}</td>
                  <td className="px-4 py-3 text-xs">{s.idle}</td>
                  <td className="px-4 py-3 text-xs">{s.usage}</td>
                  <td className="px-4 py-3"><SeverityBadge s={s.risk}/></td>
                  <td className="px-4 py-3"><StatusDot status={s.status}/></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  );
}
