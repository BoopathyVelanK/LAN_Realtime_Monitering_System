import { httpClient } from './httpClient';
import { USE_MOCKS } from '../config';
import type { AuthResponse, LoginRequest } from '../types/api';

function uuidFor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  const hex = h.toString(16).padStart(8, '0');
  return `${hex}-0000-4000-8000-${hex}0000`;
}

async function mockLogin(request: LoginRequest): Promise<AuthResponse> {
  await new Promise((r) => setTimeout(r, 400)); // feels like a real network call
  if (!request.usernameOrEmail || !request.password) {
    throw new Error('Username and password are required.');
  }
  return {
    userId: uuidFor(request.usernameOrEmail),
    username: request.usernameOrEmail,
    fullName: 'Demo Administrator',
    roles: ['ADMIN'],
    accessToken: `mock-access-${Date.now()}`,
    refreshToken: `mock-refresh-${Date.now()}`,
    accessTokenExpiresInSeconds: 900,
  };
}

export const authApi = {
  login: (request: LoginRequest): Promise<AuthResponse> =>
    USE_MOCKS ? mockLogin(request) : httpClient.post('/auth/login', request).then((r) => r.data),

  logout: (refreshToken: string): Promise<void> =>
    USE_MOCKS ? Promise.resolve() : httpClient.post('/auth/logout', { refreshToken }).then(() => undefined),
};
