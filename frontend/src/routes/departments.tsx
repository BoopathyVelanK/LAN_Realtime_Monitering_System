import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SectionCard, Meter } from "@/components/AppShell";
import { Building2 } from "lucide-react";

export const Route = createFileRoute("/departments")({
  head: () => ({ meta: [{ title: "SecureSOC — Departments" }] }),
  component: DepartmentsPage,
});

const depts = [
  { name: "Computer Science & Engineering", code: "CSE", endpoints: 42, students: 380, faculty: 22, risk: 34 },
  { name: "Information Technology", code: "IT", endpoints: 38, students: 320, faculty: 18, risk: 41 },
  { name: "Electronics & Communication", code: "ECE", endpoints: 30, students: 260, faculty: 16, risk: 22 },
  { name: "Electrical & Electronics", code: "EEE", endpoints: 22, students: 210, faculty: 14, risk: 18 },
  { name: "Mechanical Engineering", code: "ME", endpoints: 10, students: 180, faculty: 12, risk: 12 },
];

function DepartmentsPage() {
  return (
    <AppShell title="Departments" subtitle="ORGANIZATIONAL UNITS">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l:"DEPARTMENTS", v: 5 },
          { l:"TOTAL STUDENTS", v: "1,350" },
          { l:"FACULTY", v: 82 },
          { l:"AVG RISK SCORE", v: 25.4 },
        ]}/>
        <div className="grid grid-cols-2 gap-5">
          {depts.map((d,i)=>(
            <SectionCard key={i} title={d.code} action={<span className="text-[10px] text-muted-foreground tracking-widest font-bold">{d.name}</span>}>
              <div className="grid grid-cols-3 gap-4 mb-4">
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">ENDPOINTS</div><div className="text-2xl font-bold mt-1">{d.endpoints}</div></div>
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">STUDENTS</div><div className="text-2xl font-bold mt-1">{d.students}</div></div>
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">FACULTY</div><div className="text-2xl font-bold mt-1">{d.faculty}</div></div>
              </div>
              <div>
                <div className="flex justify-between text-[11px] font-bold mb-1"><span>DEPARTMENT RISK</span><span className={d.risk>35?"text-critical":""}>{d.risk} / 100</span></div>
                <Meter value={d.risk} accent={d.risk>35}/>
              </div>
            </SectionCard>
          ))}
        </div>
      </div>
    </AppShell>
  );
}
