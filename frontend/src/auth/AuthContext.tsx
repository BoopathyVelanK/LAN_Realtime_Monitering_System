import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useNavigate } from "@tanstack/react-router";
import { authApi } from "@/api/authApi";
import { registerAuthExpiredHandler } from "@/api/httpClient";
import { tokenStorage } from "@/auth/tokenStorage";
import type { AuthResponse } from "@/types/api";

const USER_STORAGE_KEY = "securesoc.user";

/** Everything about the signed-in user except the tokens themselves -
 * tokens stay in tokenStorage (see its own file for the rationale on
 * localStorage vs cookies). Kept as a plain object mirroring the fields
 * of AuthResponse the UI actually needs. */
export interface AuthUser {
  userId: string;
  username: string;
  fullName: string;
  roles: string[];
}

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  /** True until the initial localStorage hydration check has run - lets
   * consumers (e.g. AppShell's route guard) avoid redirecting to /login
   * for a split second on every hard refresh before we've had a chance
   * to check whether a session already exists. */
  isInitializing: boolean;
  login: (usernameOrEmail: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function persistUser(user: AuthUser) {
  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
}

function loadPersistedUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

function clearPersistedUser() {
  localStorage.removeItem(USER_STORAGE_KEY);
}

function toAuthUser(response: AuthResponse): AuthUser {
  return {
    userId: response.userId,
    username: response.username,
    fullName: response.fullName,
    roles: response.roles,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);

  // Hydrate from localStorage once on mount. A token with no matching
  // user record (or vice versa) is treated as an invalid session - both
  // are cleared rather than trusting a partial state.
  useEffect(() => {
    const hasToken = Boolean(tokenStorage.getAccessToken());
    const persistedUser = loadPersistedUser();
    if (hasToken && persistedUser) {
      setUser(persistedUser);
    } else {
      tokenStorage.clear();
      clearPersistedUser();
    }
    setIsInitializing(false);
  }, []);

  // httpClient calls this when a 401 survives a refresh attempt (refresh
  // token itself expired/revoked) - this is the only place session state
  // gets cleared outside of an explicit logout() call.
  useEffect(() => {
    registerAuthExpiredHandler(() => {
      tokenStorage.clear();
      clearPersistedUser();
      setUser(null);
      navigate({ to: "/login" });
    });
  }, [navigate]);

  const login = async (usernameOrEmail: string, password: string) => {
    const response = await authApi.login({ usernameOrEmail, password });
    tokenStorage.setTokens(response.accessToken, response.refreshToken);
    const authUser = toAuthUser(response);
    persistUser(authUser);
    setUser(authUser);
  };

  const logout = async () => {
    const refreshToken = tokenStorage.getRefreshToken();
    if (refreshToken) {
      // Best-effort - the backend revokes the refresh token server-side,
      // but the local session ends regardless of whether this call
      // succeeds (e.g. backend unreachable, token already expired).
      await authApi.logout(refreshToken).catch(() => undefined);
    }
    tokenStorage.clear();
    clearPersistedUser();
    setUser(null);
    navigate({ to: "/login" });
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: user !== null, isInitializing, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
