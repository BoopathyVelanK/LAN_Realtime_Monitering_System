import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SectionCard } from "@/components/AppShell";

export const Route = createFileRoute("/settings")({
  head: () => ({ meta: [{ title: "SecureSOC — Settings" }] }),
  component: SettingsPage,
});

function Row({ label, value, hint }: { label: string; value: React.ReactNode; hint?: string }) {
  return (
    <div className="flex items-center justify-between py-4 border-b border-border last:border-0">
      <div>
        <div className="text-sm font-bold">{label}</div>
        {hint && <div className="text-[11px] text-muted-foreground mt-1">{hint}</div>}
      </div>
      <div>{value}</div>
    </div>
  );
}

function SettingsPage() {
  const input = "bg-background border border-border rounded px-3 py-2 text-xs font-bold w-56 outline-none focus:border-primary";
  return (
    <AppShell title="Settings" subtitle="SYSTEM CONFIGURATION">
      <div className="px-8 pb-8 grid grid-cols-12 gap-5">
        <SectionCard title="Server Configuration" className="col-span-6">
          <Row label="Server Hostname" value={<input className={input} defaultValue="optk-soc.local" />} />
          <Row label="WebSocket Port" value={<input className={input} defaultValue="8443" />} />
          <Row label="REST API Port" value={<input className={input} defaultValue="8080" />} />
          <Row label="JWT Token TTL" value={<input className={input} defaultValue="24h" />} />
        </SectionCard>

        <SectionCard title="Endpoint Agent" className="col-span-6">
          <Row label="Heartbeat Interval" hint="seconds" value={<input className={input} defaultValue="30" />} />
          <Row label="Offline Buffer Cap" hint="events" value={<input className={input} defaultValue="50000" />} />
          <Row label="LAN Broadcast Discovery" value={<input type="checkbox" defaultChecked className="accent-[color:var(--primary)] w-5 h-5" />} />
          <Row label="Auto-start on Boot" value={<input type="checkbox" defaultChecked className="accent-[color:var(--primary)] w-5 h-5" />} />
        </SectionCard>

        <SectionCard title="Idle Thresholds (minutes)" className="col-span-6">
          <Row label="Active → Idle" value={<input className={input} defaultValue="5" />} />
          <Row label="Idle → Long Idle" value={<input className={input} defaultValue="15" />} />
          <Row label="Long Idle → Critical" value={<input className={input} defaultValue="30" />} />
        </SectionCard>

        <SectionCard title="Security" className="col-span-6">
          <Row label="Passkey Lockout (failed)" value={<input className={input} defaultValue="5" />} />
          <Row label="Lockout Duration" hint="minutes" value={<input className={input} defaultValue="5" />} />
          <Row label="Force HTTPS" value={<input type="checkbox" defaultChecked className="accent-[color:var(--primary)] w-5 h-5" />} />
          <Row label="Audit Log Retention" hint="days" value={<input className={input} defaultValue="365" />} />
        </SectionCard>

        <div className="col-span-12 flex justify-end gap-3">
          <button className="px-5 py-2.5 border border-border rounded text-xs font-bold tracking-wider hover:bg-muted">DISCARD</button>
          <button className="px-5 py-2.5 bg-primary text-primary-foreground rounded text-xs font-bold tracking-wider">SAVE CHANGES</button>
        </div>
      </div>
    </AppShell>
  );
}
