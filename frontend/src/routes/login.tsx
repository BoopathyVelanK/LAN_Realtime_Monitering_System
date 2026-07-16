import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { Shield, Lock, User } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useAuth } from "@/auth/AuthContext";

export const Route = createFileRoute("/login")({
  head: () => ({ meta: [{ title: "SecureSOC — Sign In" }] }),
  component: LoginPage,
});

function LoginPage() {
  const navigate = useNavigate();
  const { login, isAuthenticated, isInitializing } = useAuth();
  const [usernameOrEmail, setUsernameOrEmail] = useState("soc.admin@securesoc.local");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // Already signed in (e.g. hit /login directly with a valid session) -
  // bounce straight to the dashboard instead of showing the form.
  useEffect(() => {
    if (!isInitializing && isAuthenticated) {
      navigate({ to: "/", replace: true });
    }
  }, [isInitializing, isAuthenticated, navigate]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(usernameOrEmail, password);
      navigate({ to: "/" });
    } catch {
      setError("Could not sign in. Check your username and password.");
    } finally {
      setSubmitting(false);
    }
  };

  if (isInitializing || isAuthenticated) {
    return null;
  }

  return (
    <div className="min-h-screen grid grid-cols-1 lg:grid-cols-2 bg-background">
      <div className="hidden lg:flex flex-col justify-between p-12 bg-primary text-primary-foreground">
        <div>
          <div className="text-3xl font-bold" style={{ fontFamily: "Georgia, serif" }}>SECURESOC</div>
          <div className="text-[11px] tracking-widest mt-2 opacity-80">ENTERPRISE LAN MONITORING & SECURITY PLATFORM</div>
        </div>
        <div className="space-y-6">
          <div className="text-4xl font-bold leading-tight" style={{ fontFamily: "Georgia, serif" }}>
            Real-time visibility<br />across every endpoint.
          </div>
          <p className="text-sm opacity-80 max-w-md leading-relaxed">
            Monitor Windows endpoints on your LAN with agent telemetry, USB & VPN detection, idle
            analytics, exam-mode enforcement, and a fully audited trail — all from a single console.
          </p>
          <div className="grid grid-cols-3 gap-4 pt-6 text-[10px] tracking-widest">
            <div><div className="text-2xl font-bold">142</div><div className="opacity-70">ENDPOINTS</div></div>
            <div><div className="text-2xl font-bold">99.98%</div><div className="opacity-70">UPTIME SLA</div></div>
            <div><div className="text-2xl font-bold">SOC 2</div><div className="opacity-70">READY</div></div>
          </div>
        </div>
        <div className="text-[10px] tracking-widest opacity-60">© 2026 SECURESOC · v4.2.0-STABLE</div>
      </div>

      <div className="flex items-center justify-center p-8">
        <div className="w-full max-w-md">
          <div className="lg:hidden mb-8 text-center">
            <Shield className="w-10 h-10 text-primary mx-auto" />
            <div className="text-2xl font-bold mt-3" style={{ fontFamily: "Georgia, serif" }}>SECURESOC</div>
          </div>
          <div className="text-[11px] tracking-widest text-muted-foreground font-bold">SIGN IN</div>
          <h1 className="text-3xl font-bold mt-1" style={{ fontFamily: "Georgia, serif" }}>Welcome back</h1>
          <p className="text-sm text-muted-foreground mt-2">Sign in to your SOC console. Sessions are secured with TLS and audit-logged.</p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-4">
            <label className="block">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold mb-2">USERNAME / EMAIL</div>
              <div className="flex items-center gap-2 border border-border rounded px-3 py-2.5 bg-card focus-within:border-primary">
                <User className="w-4 h-4 text-muted-foreground" />
                <input
                  className="bg-transparent outline-none text-sm flex-1"
                  value={usernameOrEmail}
                  onChange={(e) => setUsernameOrEmail(e.target.value)}
                  autoComplete="username"
                />
              </div>
            </label>
            <label className="block">
              <div className="text-[10px] tracking-widest text-muted-foreground font-bold mb-2">PASSWORD</div>
              <div className="flex items-center gap-2 border border-border rounded px-3 py-2.5 bg-card focus-within:border-primary">
                <Lock className="w-4 h-4 text-muted-foreground" />
                <input
                  type="password"
                  className="bg-transparent outline-none text-sm flex-1"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                />
              </div>
            </label>

            {/* Role selection was removed here - it never sent anything to
                the backend and could imply a user gets to pick their own
                role. The signed-in user's actual role(s) come back from
                POST /auth/login (AuthResponse.roles) and are shown in the
                app header/nav after sign-in instead. */}

            <div className="flex items-center justify-between text-xs">
              <label className="flex items-center gap-2">
                <input type="checkbox" defaultChecked className="accent-[color:var(--primary)]" />
                <span>Remember this device</span>
              </label>
              <button type="button" className="text-primary font-bold">Forgot password?</button>
            </div>

            {error && <p className="text-xs text-destructive font-medium">{error}</p>}

            <button
              type="submit"
              disabled={submitting}
              className="w-full bg-primary text-primary-foreground py-3 rounded font-bold text-xs tracking-widest hover:opacity-90 disabled:opacity-60"
            >
              {submitting ? "SIGNING IN…" : "SIGN IN SECURELY"}
            </button>

            <div className="text-center text-[10px] tracking-widest text-muted-foreground pt-4">
              2FA ENABLED · SESSION TIMEOUT 30M · IP ALLOWLIST ACTIVE
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
