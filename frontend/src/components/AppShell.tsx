import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { useEffect, type ReactNode } from "react";
import {
  LayoutGrid, Monitor, AppWindow, Clock, Usb, ShieldOff, Activity, BarChart3, Gauge,
  Bell, FileText, GraduationCap, ScrollText, Settings as SettingsIcon, Search, Cpu,
  Database, Network, User, Users as UsersIcon, Building2, FlaskConical, Server,
  ShieldCheck, FileWarning, Archive, HelpCircle, LogOut, Globe, UserSquare2, Boxes,
} from "lucide-react";
import { useAuth } from "@/auth/AuthContext";
import { useEndpoints, useAlerts } from "@/api/queries";
import { useLiveFeed } from "@/ws/useLiveFeed";

type NavItem = { to: string; label: string; icon: any; action?: "sign-out" };

const navGroups: { title: string; items: NavItem[] }[] = [
  {
    title: "MONITORING",
    items: [
      { to: "/", label: "DASHBOARD", icon: LayoutGrid },
      { to: "/live-systems", label: "LIVE SYSTEMS", icon: Monitor },
      { to: "/endpoints", label: "ENDPOINTS", icon: Server },
      { to: "/running-applications", label: "APPLICATIONS", icon: AppWindow },
      { to: "/usb-monitoring", label: "USB MONITORING", icon: Usb },
      { to: "/vpn-monitoring", label: "VPN MONITORING", icon: ShieldOff },
      { to: "/idle-monitoring", label: "IDLE MONITORING", icon: Clock },
      { to: "/network-usage", label: "NETWORK USAGE", icon: Activity },
      { to: "/internet-usage", label: "INTERNET USAGE", icon: Globe },
      { to: "/student-activity", label: "STUDENT ACTIVITY", icon: UserSquare2 },
    ],
  },
  {
    title: "SECURITY",
    items: [
      { to: "/faculty", label: "FACULTY DASHBOARD", icon: GraduationCap },
      { to: "/exam-mode", label: "EXAM MODE", icon: ShieldCheck },
      { to: "/alerts", label: "ALERTS", icon: Bell },
      { to: "/risk-analysis", label: "RISK ANALYSIS", icon: Gauge },
      { to: "/detection-rules", label: "DETECTION RULES", icon: FileWarning },
      { to: "/ioc", label: "IOC MANAGEMENT", icon: ShieldOff },
    ],
  },
  {
    title: "ADMINISTRATION",
    items: [
      { to: "/reports", label: "REPORTS", icon: FileText },
      { to: "/data-usage", label: "DATA ANALYTICS", icon: BarChart3 },
      { to: "/inventory", label: "INVENTORY", icon: Boxes },
      { to: "/departments", label: "DEPARTMENTS", icon: Building2 },
      { to: "/laboratories", label: "LABORATORIES", icon: FlaskConical },
      { to: "/users", label: "USERS & ROLES", icon: UsersIcon },
      { to: "/audit-logs", label: "AUDIT LOGS", icon: ScrollText },
      { to: "/backup", label: "BACKUP & RESTORE", icon: Archive },
    ],
  },
  {
    title: "SYSTEM",
    items: [
      { to: "/settings", label: "SETTINGS", icon: SettingsIcon },
      { to: "/help", label: "HELP", icon: HelpCircle },
      { to: "/login", label: "SIGN OUT", icon: LogOut, action: "sign-out" },
    ],
  },
];

export function AppShell({
  children,
  title,
  subtitle,
  searchPlaceholder = "Search endpoints, users, alerts, IOCs…",
}: {
  children: ReactNode;
  title?: string;
  subtitle?: string;
  searchPlaceholder?: string;
}) {
  const { location } = useRouterState();
  const path = location.pathname;
  const navigate = useNavigate();
  const { user, isAuthenticated, isInitializing, logout } = useAuth();

  // Route guard: every page that renders AppShell is a protected page.
  // Redirect to /login once we've finished checking localStorage for an
  // existing session (isInitializing avoids a false redirect on first
  // paint of a hard refresh, before hydration has run).
  useEffect(() => {
    if (!isInitializing && !isAuthenticated) {
      navigate({ to: "/login", replace: true });
    }
  }, [isInitializing, isAuthenticated, navigate]);

  // Real data for the top bar / footer status strip below - these used to
  // be hardcoded (frontend integration audit): WSS/endpoint-count/alert
  // banner now come from the same hooks every other page already uses.
  // NOTE: because AppShell remounts on every route change (it's rendered
  // per-page, not once at the router root), useLiveFeed's subscription
  // reconnects on each navigation rather than staying open for the whole
  // session. That's a real inefficiency, not a correctness bug (the mock/
  // real client both reconnect quickly) - moving this to __root.tsx as a
  // single app-wide provider would fix it properly; flagged in the audit
  // doc as a follow-up rather than done here to avoid restructuring the
  // routing/provider layout in the same change as a status-bar fix.
  const { data: endpoints } = useEndpoints();
  const totalEndpoints = endpoints?.length ?? 0;
  const onlineEndpoints = endpoints?.filter((e) => e.status === "ONLINE").length ?? 0;
  const { connected } = useLiveFeed();
  // Alerts are still MOCK ONLY (no backend AlertController yet - see
  // dashboardApi.ts header) but at least this reflects that mock's actual
  // current state instead of a string that can never change.
  const { data: alerts } = useAlerts();
  const latestOpenAlert = alerts?.find((a) => a.status === "OPEN");

  // Don't flash protected content while redirecting or before we know
  // whether a session exists.
  if (isInitializing || !isAuthenticated) {
    return null;
  }

  const primaryRole = user?.roles[0] ?? "USER";
  const displayName = user?.fullName || user?.username || "";

  return (
    <div className="min-h-screen flex flex-col bg-background">
      {/* Top bar */}
      <header className="border-b border-border bg-background">
        <div className="flex items-center gap-4 px-6 py-3">
          <div className="text-primary font-bold text-xl tracking-tight" style={{ fontFamily: "Georgia, serif" }}>
            SECURESOC
          </div>
          <div className="flex-1 max-w-2xl">
            <div className="flex items-center gap-2 border border-border rounded-md px-3 py-2 bg-card">
              <Search className="w-4 h-4 text-muted-foreground" />
              <input
                className="bg-transparent outline-none text-sm flex-1 placeholder:text-muted-foreground"
                placeholder={searchPlaceholder}
              />
              <span className="text-[10px] border border-border px-1.5 py-0.5 rounded text-muted-foreground">⌘K</span>
            </div>
          </div>
          <div className="hidden md:flex items-center gap-2 text-xs border border-border rounded-md px-3 py-2 bg-card">
            <span className={`w-2 h-2 rounded-full inline-block ${connected ? "bg-primary animate-pulse" : "bg-muted-foreground"}`} />
            <span className="font-semibold">SOC-NODE: SRV-01</span>
            <span className="text-muted-foreground mx-2">|</span>
            <span>{connected ? "WSS: CONNECTED" : "WSS: OFFLINE"}</span>
          </div>
          <div className="hidden lg:flex items-center gap-3 text-muted-foreground">
            <Cpu className="w-5 h-5" />
            <Database className="w-5 h-5" />
            <Network className="w-5 h-5" />
          </div>
          <div className="flex items-center gap-3">
            <div className="text-right text-xs hidden sm:block">
              <div className="text-primary font-bold">{primaryRole}</div>
              <div className="text-muted-foreground">{displayName}</div>
            </div>
            <button
              onClick={() => void logout()}
              title="Sign out"
              className="bg-primary text-primary-foreground p-2 rounded hover:opacity-90"
            >
              <User className="w-5 h-5" />
            </button>
          </div>
        </div>
      </header>

      <div className="flex flex-1">
        {/* Sidebar */}
        <aside className="w-64 border-r border-border bg-sidebar flex flex-col shrink-0">
          <div className="p-6 border-b border-border">
            <div className="font-bold text-lg" style={{ fontFamily: "Georgia, serif" }}>SECURESOC</div>
            <div className="text-[10px] tracking-widest text-muted-foreground mt-1">ENTERPRISE LAN MONITORING</div>
          </div>

          <nav className="p-3 flex-1 overflow-y-auto">
            {navGroups.map((group) => (
              <div key={group.title} className="mb-4">
                <div className="px-4 text-[10px] tracking-widest text-muted-foreground mb-1.5 font-bold">
                  {group.title}
                </div>
                <div className="space-y-0.5">
                  {group.items.map((item) => {
                    const active = path === item.to;
                    const Icon = item.icon;

                    if (item.action === "sign-out") {
                      return (
                        <button
                          key={item.to}
                          onClick={() => void logout()}
                          className="flex items-center gap-3 px-4 py-2 rounded-md text-[11px] font-bold tracking-wider transition-colors text-sidebar-foreground hover:bg-muted w-full text-left"
                        >
                          <Icon className="w-4 h-4 shrink-0" />
                          {item.label}
                        </button>
                      );
                    }

                    return (
                      <Link
                        key={item.to}
                        to={item.to}
                        className={`flex items-center gap-3 px-4 py-2 rounded-md text-[11px] font-bold tracking-wider transition-colors ${
                          active ? "bg-primary text-primary-foreground" : "text-sidebar-foreground hover:bg-muted"
                        }`}
                      >
                        <Icon className="w-4 h-4 shrink-0" />
                        {item.label}
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </nav>

          <div className="p-6 border-t border-border flex items-center justify-between text-xs">
            <span className="font-bold tracking-wider text-muted-foreground">LAN SYNC ACTIVE</span>
            <span className={`w-2 h-2 rounded-full ${connected ? "bg-primary animate-pulse" : "bg-muted-foreground"}`} />
          </div>
        </aside>

        <main className="flex-1 overflow-auto">
          {title && (
            <div className="px-8 pt-8 pb-2">
              <div className="text-[11px] tracking-widest text-muted-foreground font-bold">{subtitle || "MODULE"}</div>
              <h1 className="text-3xl font-bold mt-1" style={{ fontFamily: "Georgia, serif" }}>{title}</h1>
            </div>
          )}
          {children}
        </main>
      </div>

      {/* Footer - was a permanently-fixed fake "UNAUTHORIZED USB DETECTED
          ON LAB-PC-17" banner + hardcoded "142/142"/uptime strings
          regardless of real state (frontend integration audit finding).
          Endpoint count is now real (useEndpoints, same as every other
          page). The alert banner reflects the mock alert feed's actual
          current state instead of one fixed string - still MOCK data
          (no backend AlertController yet, see dashboardApi.ts), but at
          least an honest reflection of that mock rather than a permanent
          false claim. Uptime removed outright: there is no backend
          concept of process/service uptime exposed anywhere to source it
          from. */}
      <footer className="border-t border-border bg-background text-[11px] flex items-center">
        {latestOpenAlert ? (
          <div className="bg-primary text-primary-foreground px-4 py-2 font-bold tracking-wider">
            ALERT (MOCK): {latestOpenAlert.title.toUpperCase()} ON {latestOpenAlert.hostname}
          </div>
        ) : (
          <div className="bg-muted px-4 py-2 font-bold tracking-wider text-muted-foreground">NO OPEN ALERTS</div>
        )}
        <div className="flex-1 flex items-center justify-around text-muted-foreground py-2 px-4 tracking-wider flex-wrap gap-2">
          <span>LAN ENDPOINTS: {onlineEndpoints} / {totalEndpoints}</span>
          <span>SECURESOC v4.2.0-STABLE</span>
        </div>
      </footer>
    </div>
  );
}

/* -------- Shared UI primitives -------- */

export function StatCard({ label, value, accent, sub }: { label: string; value: string | number; accent?: "primary" | "danger"; sub?: string }) {
  return (
    <div className="bg-card border border-border rounded-lg p-5">
      <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{label}</div>
      <div className={`text-3xl font-bold mt-3 ${accent === "primary" ? "text-primary" : accent === "danger" ? "text-critical" : ""}`}>
        {value}
      </div>
      {sub && <div className="text-[10px] tracking-wider text-muted-foreground mt-2">{sub}</div>}
    </div>
  );
}

export function SectionCard({ title, action, children, className = "" }: { title: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <div className={`bg-card border border-border rounded-lg ${className}`}>
      <div className="flex items-center justify-between p-5 border-b border-border">
        <div className="font-bold text-sm tracking-wide">{title}</div>
        {action}
      </div>
      <div className="p-5">{children}</div>
    </div>
  );
}

export function SeverityBadge({ s }: { s: "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO" | "WARNING" }) {
  if (s === "CRITICAL")
    return <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-[10px] font-bold tracking-wider rounded bg-critical text-critical-foreground">CRITICAL</span>;
  if (s === "HIGH")
    return <span className="inline-flex items-center gap-1.5 px-2.5 py-1 text-[10px] font-bold tracking-wider rounded border border-critical text-critical bg-critical/5"><span className="w-1.5 h-1.5 rounded-full bg-critical" />HIGH</span>;
  if (s === "WARNING")
    return <span className="px-2.5 py-1 text-[10px] font-bold tracking-wider rounded border border-border bg-background">WARNING</span>;
  if (s === "MEDIUM")
    return <span className="px-2.5 py-1 text-[10px] font-bold tracking-wider rounded border border-border bg-background">MEDIUM</span>;
  if (s === "LOW")
    return <span className="px-2.5 py-1 text-[10px] font-bold tracking-wider rounded border border-border bg-muted/50 text-muted-foreground">LOW</span>;
  return <span className="px-2.5 py-1 text-[10px] font-bold tracking-wider rounded border border-border bg-muted/50 text-muted-foreground">INFO</span>;
}

export function StatusDot({ status }: { status: "online" | "offline" | "idle" | "exam" }) {
  const map = {
    online: { color: "bg-green-600", label: "ONLINE" },
    offline: { color: "bg-muted-foreground", label: "OFFLINE" },
    idle: { color: "bg-yellow-500", label: "IDLE" },
    exam: { color: "bg-primary", label: "EXAM MODE" },
  } as const;
  const m = map[status];
  return (
    <span className="inline-flex items-center gap-2 text-xs font-bold tracking-wider">
      <span className={`w-2 h-2 rounded-full ${m.color}`} />
      {m.label}
    </span>
  );
}

export function Meter({ value, accent }: { value: number; accent?: boolean }) {
  return (
    <div className="h-1.5 bg-muted rounded-full overflow-hidden w-full">
      <div className={`h-full ${accent ? "bg-critical" : "bg-primary"}`} style={{ width: `${Math.min(100, value)}%` }} />
    </div>
  );
}

export function DataTable({ columns, rows }: { columns: string[]; rows: (string | number | ReactNode)[][] }) {
  return (
    <div className="bg-card border border-border rounded-lg overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-[10px] tracking-widest text-muted-foreground border-b border-border bg-muted/40">
            {columns.map((c) => <th key={c} className="px-5 py-3 font-bold">{c}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-b border-border last:border-0">
              {r.map((cell, j) => <td key={j} className="px-5 py-3 text-xs">{cell}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function KpiGrid({ cards }: { cards: { l: string; v: string | number; d?: boolean; p?: boolean }[] }) {
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-5">
      {cards.map((c, i) => (
        <div key={i} className="bg-card border border-border rounded-lg p-5">
          <div className="text-[10px] tracking-widest text-muted-foreground font-bold">{c.l}</div>
          <div className={`text-3xl font-bold mt-3 ${c.d ? "text-critical" : c.p ? "text-primary" : ""}`}>{c.v}</div>
        </div>
      ))}
    </div>
  );
}
