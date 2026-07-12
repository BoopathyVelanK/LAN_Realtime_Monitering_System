export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';
export const WS_URL = import.meta.env.VITE_WS_URL ?? API_BASE_URL.replace(/\/api$/, '/api/ws');
export const USE_MOCKS = String(import.meta.env.VITE_USE_MOCKS ?? 'true') === 'true';
