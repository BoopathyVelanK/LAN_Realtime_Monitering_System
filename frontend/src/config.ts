// Points at the real backend (Java Spring Boot, context-path /api) by
// default. Set VITE_USE_MOCKS=true in .env to run entirely against
// in-memory mock data/WebSocket — useful for frontend work when no
// backend is running yet. See .env.example.

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';
export const WS_URL = import.meta.env.VITE_WS_URL ?? API_BASE_URL.replace(/\/api$/, '/api/ws');
export const USE_MOCKS = String(import.meta.env.VITE_USE_MOCKS ?? 'true') === 'true';
console.log("USE_MOCKS:", USE_MOCKS);
console.log("API_BASE_URL:", API_BASE_URL);