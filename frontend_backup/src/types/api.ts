/**
 * These types mirror the backend's actual DTO records field-for-field
 * (com.securesoc.dto.*) — not invented shapes. Keep them in sync if the
 * backend DTOs change.
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
 * EndpointSummaryResponse, see backend EndpointStatusEvent Javadoc. */
export interface EndpointStatusEvent {
  endpointId: string;
  hostname: string;
  status: EndpointStatus;
  lastHeartbeatAt: string | null;
}

export type AlertSeverity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | string;

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
