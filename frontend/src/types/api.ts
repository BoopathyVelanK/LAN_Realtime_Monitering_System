/**
 * These types mirror the backend's actual DTO records field-for-field
 * (com.securesoc.dto.*) where a backend DTO exists. Keep them in sync if
 * the backend DTOs change.
 *
 * Backend coverage as of the Phase 4A audit (securesoc-backend/src/main/
 * java/com/securesoc/dto/ + controller/):
 *   - AuthResponse, LoginRequest, EndpointSummaryResponse: real backend
 *     DTOs exist and are wired to real controllers (AuthController,
 *     EndpointController).
 *   - AlertResponse, RiskScoreResponse, EndpointStatusEvent: CONTRACT-FIRST
 *     types only - there is no AlertController, RiskController, Alert
 *     entity, risk-scoring engine, or WebSocket push layer on the backend
 *     yet (that's Phase 4's detection engine and Phase 5's real-time
 *     layer). These shapes exist so the frontend and future backend agree
 *     on the contract in advance; nothing currently returns this data
 *     from a real endpoint. See frontend/src/api/dashboardApi.ts, which
 *     deliberately always serves these from mocks/data.ts regardless of
 *     VITE_USE_MOCKS until that backend work lands.
 */

export interface AuthResponse {
  userId: string;
  username: string;
  fullName: string;
  roles: string[];
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresInSeconds: number;
}

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export type EndpointStatus = 'ONLINE' | 'OFFLINE' | string;

export interface EndpointSummaryResponse {
  id: string;
  hostname: string;
  macAddress: string;
  ipAddress: string;
  labId: string | null;
  labName: string | null;
  status: EndpointStatus;
  lastHeartbeatAt: string | null; // Instant, ISO-8601
  osName: string | null;
  osVersion: string | null;
  cpuInfo: string | null;
  ramMb: number | null;
  diskGb: number | null;
  agentVersion: string | null;
}

/** Pushed over /topic/endpoints/status — deliberately smaller than
 * EndpointSummaryResponse, see backend EndpointStatusEvent Javadoc.
 * CONTRACT-FIRST: no WebSocket push layer exists on the backend yet
 * (Phase 5) - see header comment above. */
export interface EndpointStatusEvent {
  endpointId: string;
  hostname: string;
  status: EndpointStatus;
  lastHeartbeatAt: string | null;
}

export type AlertSeverity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | string;

/** CONTRACT-FIRST: no backend AlertController/Alert entity exists yet -
 * see header comment above. */
export interface AlertResponse {
  id: string;
  endpointId: string;
  hostname: string;
  category: string;
  severity: AlertSeverity;
  title: string;
  description: string | null;
  status: AlertStatus;
  assignedToUserId: string | null;
  assignedToUsername: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export type RiskLevel = 'SAFE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;

/** CONTRACT-FIRST: no backend RiskController/risk-scoring engine exists
 * yet - see header comment above. */
export interface RiskScoreResponse {
  endpointId: string;
  score: number;
  level: RiskLevel;
  updatedAt: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
