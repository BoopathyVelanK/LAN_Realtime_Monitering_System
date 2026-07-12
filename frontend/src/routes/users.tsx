import { createFileRoute } from "@tanstack/react-router";
import { AppShell, KpiGrid } from "@/components/AppShell";
import { Plus } from "lucide-react";

export const Route = createFileRoute("/users")({
  head: () => ({ meta: [{ title: "SecureSOC — Users & Roles" }] }),
  component: UsersPage,
});

const users = [
  { name:"Ananya Sharma", email:"admin@securesoc.local", role:"Administrator", dept:"IT-Ops", last:"just now", state:"ACTIVE", mfa:true },
  { name:"Rohan Iyer", email:"r.iyer@securesoc.local", role:"Faculty", dept:"CSE", last:"12 min ago", state:"ACTIVE", mfa:true },
  { name:"Priya Das", email:"p.das@securesoc.local", role:"Faculty", dept:"IT", last:"1h ago", state:"ACTIVE", mfa:false },
  { name:"Kunal Menon", email:"k.menon@securesoc.local", role:"Lab Assistant", dept:"ECE", last:"3h ago", state:"ACTIVE", mfa:true },
  { name:"Sara Nair", email:"s.nair@securesoc.local", role:"Auditor", dept:"Compliance", last:"2d ago", state:"INACTIVE", mfa:true },
];

function UsersPage() {
  return (
    <AppShell title="Users & Roles" subtitle="RBAC MANAGEMENT">
      <div className="px-8 pb-8">
        <KpiGrid cards={[
          { l:"USERS", v: 24 },
          { l:"ADMINS", v: 3, p:true },
          { l:"FACULTY", v: 15 },
          { l:"MFA ENROLLED", v:"92%", p:true },
        ]}/>
        <div className="flex justify-end mb-4">
          <button className="inline-flex items-center gap-1.5 bg-primary text-primary-foreground rounded px-3 py-2 text-[10px] font-bold tracking-widest"><Plus className="w-3 h-3"/>INVITE USER</button>
        </div>
        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead><tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
              {["USER","EMAIL","ROLE","DEPT","LAST ACTIVE","MFA","STATE","ACTIONS"].map(c=><th key={c} className="px-4 py-3 font-bold">{c}</th>)}
            </tr></thead>
            <tbody>
              {users.map((u,i)=>(
                <tr key={i} className="border-b border-border last:border-0">
                  <td className="px-4 py-3 text-xs font-bold flex items-center gap-2">
                    <span className="w-7 h-7 rounded-full bg-primary text-primary-foreground grid place-items-center text-[10px] font-bold">
                      {u.name.split(" ").map(p=>p[0]).join("")}
                    </span>
                    {u.name}
                  </td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground">{u.email}</td>
                  <td className="px-4 py-3"><span className="px-2 py-0.5 border border-border rounded text-[10px] font-bold tracking-widest">{u.role.toUpperCase()}</span></td>
                  <td className="px-4 py-3 text-xs">{u.dept}</td>
                  <td className="px-4 py-3 text-[11px] text-muted-foreground">{u.last}</td>
                  <td className="px-4 py-3 text-[10px] font-bold tracking-widest">{u.mfa?<span className="text-primary">ENABLED</span>:<span className="text-critical">MISSING</span>}</td>
                  <td className="px-4 py-3 text-[10px] font-bold tracking-widest">{u.state==="ACTIVE"?<span className="text-primary">ACTIVE</span>:<span className="text-muted-foreground">INACTIVE</span>}</td>
                  <td className="px-4 py-3 flex gap-1.5">
                    <button className="text-[10px] font-bold tracking-widest border border-border px-2 py-1 rounded hover:bg-muted">EDIT</button>
                    <button className="text-[10px] font-bold tracking-widest border border-border px-2 py-1 rounded hover:bg-muted text-critical">SUSPEND</button>
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
