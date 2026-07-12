import { createFileRoute } from "@tanstack/react-router";
import { AppShell, SectionCard } from "@/components/AppShell";
import { BookOpen, LifeBuoy, Keyboard, Mail } from "lucide-react";

export const Route = createFileRoute("/help")({
  head: () => ({ meta: [{ title: "SecureSOC — Help" }] }),
  component: HelpPage,
});

const shortcuts = [
  ["⌘K","Open global search"],["G then D","Go to Dashboard"],["G then E","Go to Endpoints"],["G then A","Open Alerts"],["Shift + ?","This help screen"],["Esc","Close dialog"],
];

const faqs = [
  { q:"How do endpoint agents connect?", a:"Agents auto-discover the SOC server via mDNS on the LAN and mutually authenticate with certificates issued at enrollment. Fallback: static server address in agent.conf." },
  { q:"What happens when an endpoint goes offline?", a:"Events buffer locally (up to 50k) and are flushed once LAN sync resumes. Buffered events are visible in Audit Logs with the 'offline-replay' tag." },
  { q:"How is Exam Mode enforced?", a:"Faculty starts a session with a passkey; agents lock down to a whitelist, disable USB except whitelisted vendors, and stream telemetry at 5s heartbeats." },
  { q:"Can I write my own detection rules?", a:"Yes. Detection Rules supports Sigma YAML import and a rule builder. Rules run server-side against streaming events." },
];

function HelpPage() {
  return (
    <AppShell title="Help & Documentation" subtitle="OPERATIONS GUIDE">
      <div className="px-8 pb-8 grid grid-cols-12 gap-5">
        <SectionCard title="Getting Started" className="col-span-8">
          <div className="grid grid-cols-2 gap-4">
            {[
              {icon:<BookOpen className="w-4 h-4"/>,t:"Deploy the agent",d:"Install SecureSOC Agent on every LAN endpoint. Enrollment auto-issues certificates."},
              {icon:<LifeBuoy className="w-4 h-4"/>,t:"Configure alerts",d:"Tune thresholds in Settings → Alert Thresholds and enable Sigma rules under Detection Rules."},
              {icon:<Keyboard className="w-4 h-4"/>,t:"Faculty workflow",d:"Enroll a passkey, whitelist exam apps, and start Exam Mode from the Exam Mode module."},
              {icon:<Mail className="w-4 h-4"/>,t:"Report distribution",d:"Schedule PDF/XLSX reports under Reports; delivery via email or S3 export."},
            ].map((s,i)=>(
              <div key={i} className="border border-border rounded p-4">
                <div className="flex items-center gap-2 text-[10px] font-bold tracking-widest text-primary">{s.icon}{s.t.toUpperCase()}</div>
                <div className="text-sm mt-2 leading-relaxed">{s.d}</div>
              </div>
            ))}
          </div>
        </SectionCard>

        <SectionCard title="Keyboard Shortcuts" className="col-span-4">
          <ul className="space-y-2 text-sm">
            {shortcuts.map(([k,v])=>(
              <li key={k} className="flex items-center justify-between">
                <span className="text-muted-foreground">{v}</span>
                <kbd className="px-2 py-0.5 border border-border rounded text-[10px] font-bold tracking-widest bg-muted/40">{k}</kbd>
              </li>
            ))}
          </ul>
        </SectionCard>

        <SectionCard title="Frequently Asked Questions" className="col-span-12">
          <div className="grid grid-cols-2 gap-x-8">
            {faqs.map((f,i)=>(
              <div key={i} className="py-4 border-b border-border last:border-0">
                <div className="font-bold text-sm">{f.q}</div>
                <div className="text-xs text-muted-foreground mt-1.5 leading-relaxed">{f.a}</div>
              </div>
            ))}
          </div>
        </SectionCard>
      </div>
    </AppShell>
  );
}
