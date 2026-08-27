import { httpClient } from './httpClient';
import { USE_MOCKS } from '../config';
import type {
  AlertResponse, EndpointSummaryResponse, RiskScoreResponse, PageResponse,
  MonitoringListParams, UsbEventResponse, VpnEventResponse, IdleEventResponse,
  NetworkUsageEventResponse, InternetUsageEventResponse, RunningAppSnapshotResponse,
  DepartmentResponse, LaboratoryResponse,
} from '../types/api';
import {
  generateAlerts, generateEndpoints, generateRiskScores, paginate,
  generateUsbEvents, generateVpnEvents, generateIdleEvents,
  generateNetworkUsageEvents, generateInternetUsageEvents, generateRunningAppSnapshots,
  generateDepartments, generateLaboratories,
} from '../mocks/data';

let mockEndpoints: EndpointSummaryResponse[] | null = null;
let mockAlerts: AlertResponse[] | null = null;
let mockRisk: RiskScoreResponse[] | null = null;
let mockUsbEvents: UsbEventResponse[] | null = null;
let mockVpnEvents: VpnEventResponse[] | null = null;
let mockIdleEvents: IdleEventResponse[] | null = null;
let mockNetworkUsageEvents: NetworkUsageEventResponse[] | null = null;
let mockInternetUsageEvents: InternetUsageEventResponse[] | null = null;
let mockRunningAppSnapshots: RunningAppSnapshotResponse[] | null = null;
let mockDepartments: DepartmentResponse[] | null = null;
let mockLaboratories: LaboratoryResponse[] | null = null;

function getMockEndpoints() {
  mockEndpoints ??= generateEndpoints();
  return mockEndpoints;
}
function getMockAlerts() {
  mockAlerts ??= generateAlerts(getMockEndpoints());
  return mockAlerts;
}
function getMockRisk() {
  mockRisk ??= generateRiskScores(getMockEndpoints());
  return mockRisk;
}
function getMockUsbEvents() {
  mockUsbEvents ??= generateUsbEvents(getMockEndpoints());
  return mockUsbEvents;
}
function getMockVpnEvents() {
  mockVpnEvents ??= generateVpnEvents(getMockEndpoints());
  return mockVpnEvents;
}
function getMockIdleEvents() {
  mockIdleEvents ??= generateIdleEvents(getMockEndpoints());
  return mockIdleEvents;
}
function getMockNetworkUsageEvents() {
  mockNetworkUsageEvents ??= generateNetworkUsageEvents(getMockEndpoints());
  return mockNetworkUsageEvents;
}
function getMockInternetUsageEvents() {
  mockInternetUsageEvents ??= generateInternetUsageEvents(getMockEndpoints());
  return mockInternetUsageEvents;
}
function getMockRunningAppSnapshots() {
  mockRunningAppSnapshots ??= generateRunningAppSnapshots(getMockEndpoints());
  return mockRunningAppSnapshots;
}
function getMockDepartments() {
  mockDepartments ??= generateDepartments();
  return mockDepartments;
}
function getMockLaboratories() {
  mockLaboratories ??= generateLaboratories(getMockEndpoints());
  return mockLaboratories;
}

// -----------------------------------------------------------------------
// Backend coverage (frontend integration audit, see securesoc-backend/src/
// main/java/com/securesoc/controller/):
//   - GET /endpoints                EXISTS (EndpointController)
//   - GET /monitoring/usb           EXISTS (MonitoringController, Phase 4B)
//   - GET /monitoring/vpn           EXISTS (MonitoringController, Phase 4B)
//   - GET /monitoring/idle          EXISTS (MonitoringController, Phase 4B)
//   - GET /monitoring/network-usage EXISTS (MonitoringController, Phase 4B)
//   - GET /monitoring/internet-usage EXISTS (MonitoringController, Phase 4B)
//   - GET /monitoring/running-apps  EXISTS (MonitoringController, Phase 4B)
//   - GET /monitoring/login, /logout EXIST too (Phase 4B) but no frontend
//     page consumes them yet - not wrapped here to avoid dead/unused code;
//     add getLoginEvents/getLogoutEvents here first if a page needs them.
//   - GET /departments              EXISTS (DepartmentController)
//   - GET /laboratories             EXISTS (LaboratoryController)
//   - GET /risk-scores, /risk-scores/{id} EXISTS (RiskScoreController,
//     Checkpoint C) - getRiskScore/getAllRiskScores below respect
//     USE_MOCKS like getEndpoints now that this backend is real.
//   - /alerts                       DOES NOT EXIST YET (Phase 4 detection
//     engine, Alert side). AlertResponse in types/api.ts is still a
//     contract-first type with nothing behind it.
// Every method below except getAlerts/acknowledgeAlert/resolveAlert
// respects USE_MOCKS, since their backend is real. Those three do NOT -
// they always return mock data regardless of USE_MOCKS, since calling
// httpClient for them would 404 against a real backend.
// -----------------------------------------------------------------------

function buildQuery(params?: MonitoringListParams): string {
  const search = new URLSearchParams();
  if (params?.endpointId) search.set('endpointId', params.endpointId);
  if (params?.page !== undefined) search.set('page', String(params.page));
  if (params?.size !== undefined) search.set('size', String(params.size));
  const qs = search.toString();
  return qs ? `?${qs}` : '';
}

export const dashboardApi = {
  getEndpoints: (): Promise<EndpointSummaryResponse[]> =>
    USE_MOCKS
      ? Promise.resolve(getMockEndpoints())
      : httpClient.get('/endpoints').then((r) => r.data),

  /** MOCK ONLY - no backend /alerts endpoint exists yet. See file header. */
  getAlerts: (params?: { endpointId?: string; status?: string }): Promise<AlertResponse[]> =>
    Promise.resolve(
      getMockAlerts().filter(
        (a) => (!params?.endpointId || a.endpointId === params.endpointId) && (!params?.status || a.status === params.status),
      ),
    ),

  /** Real backend as of Checkpoint C (RiskScoreController) - respects
   * USE_MOCKS like getEndpoints. A missing RiskScore row (endpoint never
   * detected against) is a 404 from the real backend, same as any other
   * not-found case - this is intentionally not masked with a synthetic
   * SAFE/0 fallback here; callers see the rejected promise. */
  getRiskScore: (endpointId: string): Promise<RiskScoreResponse> =>
    USE_MOCKS
      ? Promise.resolve(getMockRisk().find((r) => r.endpointId === endpointId)!)
      : httpClient.get(`/risk-scores/${endpointId}`).then((r) => r.data),

  /** Real backend as of Checkpoint C (RiskScoreController) - respects
   * USE_MOCKS like getEndpoints. The `_endpoints` param is unused in both
   * branches (mock generation and the real endpoint both derive the full
   * set server/mock-side); kept only to avoid changing this function's
   * signature and rippling into its one call site (useRiskScores). */
  getAllRiskScores: (_endpoints: EndpointSummaryResponse[]): Promise<RiskScoreResponse[]> =>
    USE_MOCKS
      ? Promise.resolve(getMockRisk())
      : httpClient.get('/risk-scores').then((r) => r.data),

  /** MOCK ONLY - no backend /alerts/{id}/acknowledge endpoint exists yet. */
  acknowledgeAlert: (id: string, _comment?: string): Promise<AlertResponse> =>
    mockUpdateAlertStatus(id, 'ACKNOWLEDGED'),

  /** MOCK ONLY - no backend /alerts/{id}/resolve endpoint exists yet. */
  resolveAlert: (id: string, _comment?: string): Promise<AlertResponse> =>
    mockUpdateAlertStatus(id, 'RESOLVED'),

  // ---------------------------------------------------------------
  // Phase 4B - real backend, respects USE_MOCKS like getEndpoints.
  // ---------------------------------------------------------------

  getUsbEvents: (params?: MonitoringListParams): Promise<PageResponse<UsbEventResponse>> =>
    USE_MOCKS
      ? Promise.resolve(paginate(
          getMockUsbEvents().filter((e) => !params?.endpointId || e.endpointId === params.endpointId),
          params?.page ?? 0, params?.size ?? 20,
        ))
      : httpClient.get(`/monitoring/usb${buildQuery(params)}`).then((r) => r.data),

  getVpnEvents: (params?: MonitoringListParams): Promise<PageResponse<VpnEventResponse>> =>
    USE_MOCKS
      ? Promise.resolve(paginate(
          getMockVpnEvents().filter((e) => !params?.endpointId || e.endpointId === params.endpointId),
          params?.page ?? 0, params?.size ?? 20,
        ))
      : httpClient.get(`/monitoring/vpn${buildQuery(params)}`).then((r) => r.data),

  getIdleEvents: (params?: MonitoringListParams): Promise<PageResponse<IdleEventResponse>> =>
    USE_MOCKS
      ? Promise.resolve(paginate(
          getMockIdleEvents().filter((e) => !params?.endpointId || e.endpointId === params.endpointId),
          params?.page ?? 0, params?.size ?? 20,
        ))
      : httpClient.get(`/monitoring/idle${buildQuery(params)}`).then((r) => r.data),

  getNetworkUsageEvents: (params?: MonitoringListParams): Promise<PageResponse<NetworkUsageEventResponse>> =>
    USE_MOCKS
      ? Promise.resolve(paginate(
          getMockNetworkUsageEvents().filter((e) => !params?.endpointId || e.endpointId === params.endpointId),
          params?.page ?? 0, params?.size ?? 30,
        ))
      : httpClient.get(`/monitoring/network-usage${buildQuery(params)}`).then((r) => r.data),

  getInternetUsageEvents: (params?: MonitoringListParams): Promise<PageResponse<InternetUsageEventResponse>> =>
    USE_MOCKS
      ? Promise.resolve(paginate(
          getMockInternetUsageEvents().filter((e) => !params?.endpointId || e.endpointId === params.endpointId),
          params?.page ?? 0, params?.size ?? 30,
        ))
      : httpClient.get(`/monitoring/internet-usage${buildQuery(params)}`).then((r) => r.data),

  getRunningAppSnapshots: (params?: MonitoringListParams): Promise<PageResponse<RunningAppSnapshotResponse>> =>
    USE_MOCKS
      ? Promise.resolve(paginate(
          getMockRunningAppSnapshots().filter((e) => !params?.endpointId || e.endpointId === params.endpointId),
          params?.page ?? 0, params?.size ?? 20,
        ))
      : httpClient.get(`/monitoring/running-apps${buildQuery(params)}`).then((r) => r.data),

  // ---------------------------------------------------------------
  // Departments / Laboratories - real backend, respects USE_MOCKS.
  // No pagination on either backend endpoint (both return a plain List,
  // same as getEndpoints), so no buildQuery() needed here.
  // ---------------------------------------------------------------

  getDepartments: (): Promise<DepartmentResponse[]> =>
    USE_MOCKS
      ? Promise.resolve(getMockDepartments())
      : httpClient.get('/departments').then((r) => r.data),

  getLaboratories: (): Promise<LaboratoryResponse[]> =>
    USE_MOCKS
      ? Promise.resolve(getMockLaboratories())
      : httpClient.get('/laboratories').then((r) => r.data),
};

function mockUpdateAlertStatus(id: string, status: AlertResponse['status']): Promise<AlertResponse> {
  const list = getMockAlerts();
  const idx = list.findIndex((a) => a.id === id);
  if (idx === -1) return Promise.reject(new Error('Alert not found'));
  const updated: AlertResponse = {
    ...list[idx],
    status,
    updatedAt: new Date().toISOString(),
    resolvedAt: status === 'RESOLVED' ? new Date().toISOString() : list[idx].resolvedAt,
  };
  list[idx] = updated;
  return Promise.resolve(updated);
}
