// The backend issues access + refresh tokens in the login response body
// (not cookies — see AuthResponse), so a SPA talking to this API has to
// hold them itself. localStorage (not sessionStorage) so a refresh/new
// tab doesn't force a re-login; the access token is short-lived
// (accessTokenExpiresInSeconds) and every request still goes through the
// backend's own auth checks regardless of where the token sits client-side.

const ACCESS_TOKEN_KEY = 'securesoc.accessToken';
const REFRESH_TOKEN_KEY = 'securesoc.refreshToken';

export const tokenStorage = {
  getAccessToken: () => localStorage.getItem(ACCESS_TOKEN_KEY),
  getRefreshToken: () => localStorage.getItem(REFRESH_TOKEN_KEY),
  setTokens: (accessToken: string, refreshToken: string) => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clear: () => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};
