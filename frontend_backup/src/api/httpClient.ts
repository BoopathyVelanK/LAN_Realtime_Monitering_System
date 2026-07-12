import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from '../config';
import { tokenStorage } from '../auth/tokenStorage';
import type { AuthResponse } from '../types/api';

let onAuthExpired: (() => void) | null = null;
export function registerAuthExpiredHandler(handler: () => void) {
  onAuthExpired = handler;
}

export const httpClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

httpClient.interceptors.request.use((config) => {
  const token = tokenStorage.getAccessToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStorage.getRefreshToken();
  if (!refreshToken) return null;
  try {
    const response = await axios.post<AuthResponse>(`${API_BASE_URL}/auth/refresh`, { refreshToken });
    tokenStorage.setTokens(response.data.accessToken, response.data.refreshToken);
    return response.data.accessToken;
  } catch {
    return null;
  }
}

httpClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

    if (error.response?.status === 401 && original && !original._retried) {
      original._retried = true;

      refreshPromise ??= refreshAccessToken().finally(() => {
        refreshPromise = null;
      });
      const newToken = await refreshPromise;

      if (newToken) {
        original.headers.set('Authorization', `Bearer ${newToken}`);
        return httpClient(original);
      }

      tokenStorage.clear();
      onAuthExpired?.();
    }

    return Promise.reject(error);
  },
);
