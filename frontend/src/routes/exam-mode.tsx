import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SectionCard } from "@/components/AppShell";
import { GraduationCap, Lock, Pause, Play, ShieldCheck } from "lucide-react";
import { useState } from "react";

export const Route = createFileRoute("/exam-mode")({
  head: () => ({ meta: [{ title: "SecureSOC — Exam Mode" }] }),
  component: ExamMode,
});

const labs = ["LAB-A (32 PCs)", "LAB-B (28 PCs)", "LAB-C (24 PCs)", "LAB-D (30 PCs)", "LAB-E (28 PCs)"];

function ExamMode() {
  const [enabled, setEnabled] = useState(false);
  const [pass, setPass] = useState("");
  return (
    <AppShell title="Exam Mode" subtitle="MONITORING CONTROL">
      <div className="px-8 pb-8 grid grid-cols-12 gap-5">
        <div className="col-span-8 space-y-5">
          <div className={`rounded-lg p-6 border ${enabled ? "bg-primary text-primary-foreground border-primary" : "bg-card border-border"}`}>
            <div className="flex items-center gap-4">
              <div className={`w-14 h-14 rounded-lg flex items-center justify-center ${enabled ? "bg-black/20" : "bg-primary/10 text-primary"}`}>
                <GraduationCap className="w-7 h-7" />
              </div>
              <div>
                <div className="text-[11px] tracking-widest font-bold opacity-80">CURRENT STATE</div>
                <div className="text-2xl font-bold mt-1">{enabled ? "MONITORING PAUSED (EXAM MODE)" : "MONITORING ACTIVE"}</div>
              </div>
              <button
                onClick={() => setEnabled(!enabled)}
                className={`ml-auto px-6 py-3 rounded text-xs font-bold tracking-wider flex items-center gap-2 ${enabled ? "bg-background text-foreground" : "bg-primary text-primary-foreground"}`}
              >
                {enabled ? <><Play className="w-4 h-4"/>RESUME MONITORING</> : <><Pause className="w-4 h-4"/>ENABLE EXAM MODE</>}
              </button>
            </div>
          </div>

          <SectionCard title="Faculty Passkey Verification">
            <div className="flex items-center gap-3">
              <Lock className="w-5 h-5 text-muted-foreground" />
              <input
                type="password"
                value={pass}
                onChange={(e) => setPass(e.target.value)}
                placeholder="Enter faculty passkey"
                className="flex-1 bg-background border border-border rounded px-4 py-3 text-sm outline-none focus:border-primary"
              />
              <button className="bg-primary text-primary-foreground px-6 py-3 rounded text-xs font-bold tracking-wider">VERIFY</button>
            </div>
            <div className="text-[11px] text-muted-foreground mt-3">After 5 failed attempts, verification will lock for 5 minutes. Every attempt is logged in audit trail.</div>
          </SectionCard>

          <SectionCard title="Select Labs to Pause">
            <div className="grid grid-cols-2 gap-3">
              {labs.map((l) => (
                <label key={l} className="flex items-center gap-3 border border-border rounded p-3 hover:bg-muted cursor-pointer">
                  <input type="checkbox" className="accent-[color:var(--primary)]" />
                  <span className="text-xs font-bold">{l}</span>
                </label>
              ))}
            </div>
          </SectionCard>
        </div>

        <SectionCard title="What Pauses" className="col-span-4">
          <ul className="space-y-2 text-xs">
            {["Idle Monitoring","Running Applications","USB Monitoring","Network Monitoring","Data Usage Tracking","Risk Calculations","Alert Generation"].map((x) => (
              <li key={x} className="flex items-center gap-2"><Pause className="w-3.5 h-3.5 text-muted-foreground" />{x}</li>
            ))}
          </ul>
          <div className="text-[11px] tracking-widest text-muted-foreground font-bold mt-5 mb-2">CONTINUES</div>
          <ul className="space-y-2 text-xs">
            {["Lightweight Heartbeat","WebSocket Connection"].map((x) => (
              <li key={x} className="flex items-center gap-2"><ShieldCheck className="w-3.5 h-3.5 text-primary" />{x}</li>
            ))}
          </ul>
        </SectionCard>
      </div>
    </AppShell>
  );
}
