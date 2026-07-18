import { httpClient } from './httpClient';
import { USE_MOCKS } from '../config';
import type { AlertResponse, EndpointSummaryResponse, RiskScoreResponse } from '../types/api';
import { generateAlerts, generateEndpoints, generateRiskScores } from '../mocks/data';

let mockEndpoints: EndpointSummaryResponse[] | null = null;
let mockAlerts: AlertResponse[] | null = null;
let mockRisk: RiskScoreResponse[] | null = null;

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

// -----------------------------------------------------------------------
// Backend coverage (Phase 4A audit, see securesoc-backend/src/main/java/
// com/securesoc/controller/):
//   - GET /endpoints        EXISTS (EndpointController) -> getEndpoints
//     respects USE_MOCKS and calls the real backend when it's false.
//   - /alerts, /alerts/*    DOES NOT EXIST YET. There is no AlertController
//     and no Alert entity/DTO on the backend - AlertResponse in
//     types/api.ts is a contract-first type with nothing behind it.
//   - /risk/*               DOES NOT EXIST YET, same story - no
//     RiskController, no risk-scoring engine (that's Phase 4's detection
//     engine work, not built).
// Until that Phase 4 backend work lands, the methods below MUST NOT call
// httpClient for alerts/risk regardless of USE_MOCKS - doing so would
// silently 404 against a real backend. They always return mock data,
// clearly so, rather than pretending to be USE_MOCKS-aware for endpoints
// that don't exist. Update this file (and re-enable the USE_MOCKS branch)
// once those controllers exist.
// -----------------------------------------------------------------------

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

  /** MOCK ONLY - no backend /risk/{id} endpoint exists yet. See file header. */
  getRiskScore: (endpointId: string): Promise<RiskScoreResponse> =>
    Promise.resolve(getMockRisk().find((r) => r.endpointId === endpointId)!),

  /** MOCK ONLY - no backend /risk/{id} endpoint exists yet. See file header. */
  getAllRiskScores: (_endpoints: EndpointSummaryResponse[]): Promise<RiskScoreResponse[]> =>
    Promise.resolve(getMockRisk()),

  /** MOCK ONLY - no backend /alerts/{id}/acknowledge endpoint exists yet. */
  acknowledgeAlert: (id: string, _comment?: string): Promise<AlertResponse> =>
    mockUpdateAlertStatus(id, 'ACKNOWLEDGED'),

  /** MOCK ONLY - no backend /alerts/{id}/resolve endpoint exists yet. */
  resolveAlert: (id: string, _comment?: string): Promise<AlertResponse> =>
    mockUpdateAlertStatus(id, 'RESOLVED'),
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
