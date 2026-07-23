/**
 * These types mirror the backend's actual DTO records field-for-field
 * (com.securesoc.dto.*) where a backend DTO exists. Keep them in sync if
 * the backend DTOs change.
 *
 * Backend coverage as of the frontend integration audit
 * (securesoc-backend/src/main/java/com/securesoc/dto/ + controller/):
 *   - AuthResponse, LoginRequest, EndpointSummaryResponse, PageResponse,
 *     the 8 monitoring event *EventResponse / RunningAppSnapshotResponse
 *     types, DepartmentResponse/LaboratoryResponse, and (as of Phase 6)
 *     EndpointStatusEvent below: real backend DTOs exist and are wired to
 *     real controllers/services (AuthController, EndpointController,
 *     MonitoringController's GET endpoints, DepartmentController,
 *     LaboratoryController, and WebSocketEndpointEventPublisher for
 *     EndpointStatusEvent specifically).
 *   - AlertResponse, RiskScoreResponse: still CONTRACT-FIRST types only -
 *     there is no AlertController, RiskController, Alert entity, or
 *     risk-scoring engine on the backend yet. These shapes exist so the
 *     frontend and future backend agree on the contract in advance;
 *     nothing currently returns this data from a real endpoint. See
 *     frontend/src/api/dashboardApi.ts, which deliberately always serves
 *     these from mocks/data.ts regardless of VITE_USE_MOCKS until that
 *     backend work lands.
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
 * Real as of Phase 6: com.securesoc.service.WebSocketEndpointEventPublisher
 * publishes this exact shape whenever a device transitions ONLINE/OFFLINE
 * (heartbeat bringing it back online, or the offline sweeper marking it
 * stale) - not on every heartbeat. labName is additive on top of the
 * shape this type originally had. */
export interface EndpointStatusEvent {
  endpointId: string;
  hostname: string;
  status: EndpointStatus;
  lastHeartbeatAt: string | null;
  labName: string | null;
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

// -------------------------------------------------------------------
// Phase 4B — monitoring read endpoints (GET /monitoring/**). Every list
// endpoint returns this same envelope shape (mirrors backend
// com.securesoc.dto.PageResponse<T> exactly).
// -------------------------------------------------------------------

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Common query params accepted by every monitoring list endpoint. */
export interface MonitoringListParams {
  endpointId?: string;
  page?: number;
  size?: number;
}

export interface LoginEventResponse {
  id: string;
  endpointId: string;
  hostname: string;
  osUsername: string;
  sessionId: string | null;
  loginTime: string;
  receivedAt: string;
}

export interface LogoutEventResponse {
  id: string;
  endpointId: string;
  hostname: string;
  osUsername: string;
  sessionId: string | null;
  logoutTime: string;
  receivedAt: string;
}

export type UsbEventAction = 'CONNECTED' | 'DISCONNECTED' | null;

export interface UsbEventResponse {
  id: string;
  endpointId: string;
  hostname: string;
  deviceName: string | null;
  deviceId: string | null;
  vendorId: string | null;
  productId: string | null;
  action: UsbEventAction;
  eventTime: string;
  receivedAt: string;
}

export interface VpnEventResponse {
  id: string;
  endpointId: string;
  hostname: string;
  adapterName: string | null;
  active: boolean;
  detectedAt: string;
}

export interface IdleEventResponse {
  id: string;
  endpointId: string;
  hostname: string;
  idleSeconds: number;
  recordedAt: string;
}

export interface NetworkUsageEventResponse {
  id: string;
  endpointId: string;
  hostname: string;
  bytesSent: number;
  bytesReceived: number;
  interfaceName: string | null;
  recordedAt: string;
}

export interface InternetUsageEventResponse {
  id: string;
  endpointId: string;
  hostname: string;
  uploadMb: number;
  downloadMb: number;
  periodSeconds: number;
  recordedAt: string;
}

export interface RunningAppEntry {
  processName: string | null;
  windowTitle: string | null;
  pid: number | null;
}

export interface RunningAppSnapshotResponse {
  id: string;
  endpointId: string;
  hostname: string;
  capturedAt: string;
  apps: RunningAppEntry[];
}

// -------------------------------------------------------------------
// Departments / Laboratories (GET /departments, GET /laboratories) —
// mirrors backend com.securesoc.dto.DepartmentResponse /
// LaboratoryResponse exactly. Read-only; laboratoryCount/endpointCount/
// onlineEndpointCount are all server-derived, not stored columns.
// -------------------------------------------------------------------

export interface DepartmentResponse {
  id: string;
  name: string;
  code: string;
  laboratoryCount: number;
}

export interface LaboratoryResponse {
  id: string;
  name: string;
  code: string;
  departmentId: string | null;
  departmentName: string | null;
  capacity: number;
  endpointCount: number;
  onlineEndpointCount: number;
}
