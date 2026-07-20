import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid, SectionCard, Meter } from "@/components/AppShell";
import { useDepartments, useLaboratories } from "@/api/queries";

export const Route = createFileRoute("/departments")({
  head: () => ({ meta: [{ title: "SecureSOC — Departments" }] }),
  component: DepartmentsPage,
});

// Frontend integration audit: NAME/CODE/LAB COUNT/ENDPOINT COUNT now come
// from the real GET /departments + GET /laboratories endpoints
// (DepartmentController/LaboratoryController - endpoint counts are summed
// client-side from each department's laboratories). STUDENTS/FACULTY/RISK
// stay mock and are labeled (MOCK) below - there is no student/faculty
// enrollment model or risk-scoring engine on the backend yet.
function mockAcademicsFor(code: string) {
  let h = 0;
  for (let i = 0; i < code.length; i++) h = (h * 31 + code.charCodeAt(i)) >>> 0;
  return { students: 150 + (h % 250), faculty: 8 + (h % 20), risk: h % 60 };
}

function DepartmentsPage() {
  const { data: departments, isLoading, isError } = useDepartments();
  const { data: laboratories } = useLaboratories();

  const rows = (departments ?? []).map((d) => {
    const endpoints = (laboratories ?? [])
      .filter((lab) => lab.departmentId === d.id)
      .reduce((sum, lab) => sum + lab.endpointCount, 0);
    return { ...d, endpoints, ...mockAcademicsFor(d.code) };
  });

  const totalStudents = rows.reduce((s, r) => s + r.students, 0);
  const totalFaculty = rows.reduce((s, r) => s + r.faculty, 0);
  const avgRisk = rows.length ? Math.round((rows.reduce((s, r) => s + r.risk, 0) / rows.length) * 10) / 10 : 0;

  return (
    <AppShell title="Departments" subtitle="ORGANIZATIONAL UNITS">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l: "DEPARTMENTS", v: rows.length },
          { l: "TOTAL STUDENTS (MOCK)", v: totalStudents.toLocaleString() },
          { l: "FACULTY (MOCK)", v: totalFaculty },
          { l: "AVG RISK SCORE (MOCK)", v: avgRisk },
        ]} />

        {isLoading && <div className="text-xs text-muted-foreground mb-4">Loading departments…</div>}
        {isError && <div className="text-xs text-critical mb-4">Could not load departments from the backend.</div>}
        {!isLoading && !isError && rows.length === 0 && (
          <div className="text-xs text-muted-foreground mb-4">No departments have been configured yet.</div>
        )}

        <div className="grid grid-cols-2 gap-5">
          {rows.map((d) => (
            <SectionCard key={d.id} title={d.code} action={<span className="text-[10px] text-muted-foreground tracking-widest font-bold">{d.name}</span>}>
              <div className="grid grid-cols-4 gap-4 mb-4">
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">LABS</div><div className="text-2xl font-bold mt-1">{d.laboratoryCount}</div></div>
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">ENDPOINTS</div><div className="text-2xl font-bold mt-1">{d.endpoints}</div></div>
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">STUDENTS (MOCK)</div><div className="text-2xl font-bold mt-1">{d.students}</div></div>
                <div><div className="text-[10px] text-muted-foreground tracking-widest font-bold">FACULTY (MOCK)</div><div className="text-2xl font-bold mt-1">{d.faculty}</div></div>
              </div>
              <div>
                <div className="flex justify-between text-[11px] font-bold mb-1"><span>DEPARTMENT RISK (MOCK)</span><span className={d.risk > 35 ? "text-critical" : ""}>{d.risk} / 100</span></div>
                <Meter value={d.risk} accent={d.risk > 35} />
              </div>
            </SectionCard>
          ))}
        </div>
      </div>
    </AppShell>
  );
}
