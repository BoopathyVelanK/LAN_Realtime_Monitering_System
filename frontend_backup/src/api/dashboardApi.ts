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

export const dashboardApi = {
  getEndpoints: (): Promise<EndpointSummaryResponse[]> =>
    USE_MOCKS
      ? Promise.resolve(getMockEndpoints())
      : httpClient.get('/endpoints').then((r) => r.data),

  getAlerts: (params?: { endpointId?: string; status?: string }): Promise<AlertResponse[]> =>
    USE_MOCKS
      ? Promise.resolve(
          getMockAlerts().filter(
            (a) => (!params?.endpointId || a.endpointId === params.endpointId) && (!params?.status || a.status === params.status),
          ),
        )
      : httpClient.get('/alerts', { params }).then((r) => r.data),

  getRiskScore: (endpointId: string): Promise<RiskScoreResponse> =>
    USE_MOCKS
      ? Promise.resolve(getMockRisk().find((r) => r.endpointId === endpointId)!)
      : httpClient.get(`/risk/${endpointId}`).then((r) => r.data),

  getAllRiskScores: (endpoints: EndpointSummaryResponse[]): Promise<RiskScoreResponse[]> =>
    USE_MOCKS
      ? Promise.resolve(getMockRisk())
      : Promise.all(endpoints.map((e) => httpClient.get(`/risk/${e.id}`).then((r) => r.data as RiskScoreResponse).catch(() => null))).then(
          (results) => results.filter((r): r is RiskScoreResponse => r !== null),
        ),

  acknowledgeAlert: (id: string, comment?: string): Promise<AlertResponse> =>
    USE_MOCKS
      ? mockUpdateAlertStatus(id, 'ACKNOWLEDGED')
      : httpClient.post(`/alerts/${id}/acknowledge`, comment ? { comment } : undefined).then((r) => r.data),

  resolveAlert: (id: string, comment?: string): Promise<AlertResponse> =>
    USE_MOCKS
      ? mockUpdateAlertStatus(id, 'RESOLVED')
      : httpClient.post(`/alerts/${id}/resolve`, comment ? { comment } : undefined).then((r) => r.data),
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
