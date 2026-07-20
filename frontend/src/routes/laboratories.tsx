import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SectionCard, StatusDot } from "@/components/AppShell";
import { useLaboratories } from "@/api/queries";

export const Route = createFileRoute("/laboratories")({
  head: () => ({ meta: [{ title: "SecureSOC — Laboratories" }] }),
  component: LabsPage,
});

// Frontend integration audit: NAME/CODE/DEPARTMENT/CAPACITY/ACTIVE (online
// endpoint count) now come from the real GET /laboratories endpoint
// (LaboratoryController). EXAM MODE and RISK stay mock and are labeled
// (MOCK) below - there is no exam-mode state or risk-scoring engine on
// the backend yet.
function mockExamAndRiskFor(code: string) {
  let h = 0;
  for (let i = 0; i < code.length; i++) h = (h * 31 + code.charCodeAt(i)) >>> 0;
  return { exam: h % 4 === 0, risk: h % 60 };
}

function LabsPage() {
  const { data: labs, isLoading, isError } = useLaboratories();
  const rows = (labs ?? []).map((l) => ({ ...l, ...mockExamAndRiskFor(l.code) }));

  const totalActive = rows.reduce((s, l) => s + l.onlineEndpointCount, 0);
  const totalCapacity = rows.reduce((s, l) => s + l.capacity, 0);
  const examCount = rows.filter((l) => l.exam).length;
  const occupancy = totalCapacity > 0 ? Math.round((totalActive / totalCapacity) * 100) : 0;

  return (
    <AppShell title="Laboratories" subtitle="PHYSICAL LAB REGISTRY">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l: "LABS", v: rows.length },
          { l: "ACTIVE NOW", v: totalActive, p: true },
          { l: "IN EXAM MODE (MOCK)", v: examCount, p: true },
          { l: "OCCUPANCY", v: `${occupancy}%` },
        ]} />

        {isLoading && <div className="text-xs text-muted-foreground mb-4">Loading laboratories…</div>}
        {isError && <div className="text-xs text-critical mb-4">Could not load laboratories from the backend.</div>}
        {!isLoading && !isError && rows.length === 0 && (
          <div className="text-xs text-muted-foreground mb-4">No laboratories have been configured yet.</div>
        )}

        <div className="grid grid-cols-2 gap-5">
          {rows.map((l) => (
            <SectionCard key={l.id} title={l.code} action={
              l.exam
                ? <span className="px-2.5 py-1 bg-primary text-primary-foreground text-[10px] font-bold tracking-widest rounded">EXAM MODE (MOCK)</span>
                : <StatusDot status="online" />
            }>
              <div className="text-sm font-bold" style={{ fontFamily: "Georgia, serif" }}>{l.name}</div>
              <div className="text-[11px] text-muted-foreground tracking-widest mt-1">DEPT: {l.departmentName ?? "UNASSIGNED"}</div>
              <div className="grid grid-cols-3 gap-4 mt-4">
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">CAPACITY</div><div className="text-xl font-bold mt-1">{l.capacity}</div></div>
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">ACTIVE</div><div className="text-xl font-bold mt-1 text-primary">{l.onlineEndpointCount}</div></div>
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">RISK (MOCK)</div><div className={`text-xl font-bold mt-1 ${l.risk > 35 ? "text-critical" : ""}`}>{l.risk}</div></div>
              </div>
            </SectionCard>
          ))}
        </div>
      </div>
    </AppShell>
  );
}
