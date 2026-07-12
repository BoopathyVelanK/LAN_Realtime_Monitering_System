import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SectionCard, StatusDot } from "@/components/AppShell";

export const Route = createFileRoute("/laboratories")({
  head: () => ({ meta: [{ title: "SecureSOC — Laboratories" }] }),
  component: LabsPage,
});

const labs = [
  { code: "Lab-A", name: "AI & Data Science Lab", dept: "CSE", capacity: 40, active: 38, exam: true, risk: 42 },
  { code: "Lab-B", name: "Networking Lab", dept: "IT", capacity: 30, active: 24, exam: false, risk: 28 },
  { code: "Lab-C", name: "Programming Lab", dept: "CSE", capacity: 40, active: 39, exam: true, risk: 18 },
  { code: "Lab-D", name: "Digital Electronics Lab", dept: "ECE", capacity: 30, active: 12, exam: false, risk: 12 },
  { code: "Lab-E", name: "Power Systems Lab", dept: "EEE", capacity: 20, active: 5, exam: false, risk: 8 },
];

function LabsPage() {
  return (
    <AppShell title="Laboratories" subtitle="PHYSICAL LAB REGISTRY">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l:"LABS", v: 12 },
          { l:"ACTIVE NOW", v: 8, p:true },
          { l:"IN EXAM MODE", v: 3, p:true },
          { l:"OCCUPANCY", v:"84%" },
        ]}/>
        <div className="grid grid-cols-2 gap-5">
          {labs.map((l,i)=>(
            <SectionCard key={i} title={l.code} action={
              l.exam
                ? <span className="px-2.5 py-1 bg-primary text-primary-foreground text-[10px] font-bold tracking-widest rounded">EXAM MODE</span>
                : <StatusDot status="online"/>
            }>
              <div className="text-sm font-bold" style={{fontFamily:"Georgia, serif"}}>{l.name}</div>
              <div className="text-[11px] text-muted-foreground tracking-widest mt-1">DEPT: {l.dept}</div>
              <div className="grid grid-cols-3 gap-4 mt-4">
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">CAPACITY</div><div className="text-xl font-bold mt-1">{l.capacity}</div></div>
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">ACTIVE</div><div className="text-xl font-bold mt-1 text-primary">{l.active}</div></div>
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">RISK</div><div className={`text-xl font-bold mt-1 ${l.risk>35?"text-critical":""}`}>{l.risk}</div></div>
              </div>
            </SectionCard>
          ))}
        </div>
      </div>
    </AppShell>
  );
}
