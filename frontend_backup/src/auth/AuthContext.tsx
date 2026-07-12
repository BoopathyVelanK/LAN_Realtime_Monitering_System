import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/authApi';
import { registerAuthExpiredHandler } from '../api/httpClient';
import { tokenStorage } from './tokenStorage';
import type { AuthResponse, LoginRequest } from '../types/api';

interface CurrentUser {
  userId: string;
  username: string;
  fullName: string;
  roles: string[];
}

interface AuthContextValue {
  user: CurrentUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (request: LoginRequest) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function userFromAuthResponse(response: AuthResponse): CurrentUser {
  return {
    userId: response.userId,
    username: response.username,
    fullName: response.fullName,
    roles: response.roles,
  };
}

const USER_CACHE_KEY = 'securesoc.user';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(() => {
    const cached = localStorage.getItem(USER_CACHE_KEY);
    const hasToken = !!tokenStorage.getAccessToken();
    return cached && hasToken ? (JSON.parse(cached) as CurrentUser) : null;
  });
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    registerAuthExpiredHandler(() => {
      setUser(null);
      localStorage.removeItem(USER_CACHE_KEY);
    });
  }, []);

  const login = async (request: LoginRequest) => {
    setIsLoading(true);
    try {
      const response = await authApi.login(request);
      tokenStorage.setTokens(response.accessToken, response.refreshToken);
      const currentUser = userFromAuthResponse(response);
      localStorage.setItem(USER_CACHE_KEY, JSON.stringify(currentUser));
      setUser(currentUser);
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    const refreshToken = tokenStorage.getRefreshToken();
    if (refreshToken) authApi.logout(refreshToken).catch(() => undefined);
    tokenStorage.clear();
    localStorage.removeItem(USER_CACHE_KEY);
    setUser(null);
  };

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: !!user, isLoading, login, logout }),
    [user, isLoading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
