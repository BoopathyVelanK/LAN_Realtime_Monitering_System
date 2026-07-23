import type { AlertResponse, EndpointStatusEvent, RiskScoreResponse } from '../types/api';
import { generateEndpoints } from './data';

export interface MockLiveFeedHandlers {
  onAlert?: (alert: AlertResponse) => void;
  onStatus?: (event: EndpointStatusEvent) => void;
  onRisk?: (risk: RiskScoreResponse) => void;
}

const endpoints = generateEndpoints();
const CATEGORIES: Array<{ category: string; severity: AlertResponse['severity']; title: string }> = [
  { category: 'USB', severity: 'MEDIUM', title: 'Unrecognized USB storage device inserted' },
  { category: 'PROCESS', severity: 'CRITICAL', title: 'Suspicious PowerShell execution detected' },
  { category: 'AUTH', severity: 'HIGH', title: 'Repeated failed login attempts' },
  { category: 'VPN', severity: 'LOW', title: 'VPN adapter activated outside policy hours' },
];

let counter = 0;

export function startMockLiveFeed(handlers: MockLiveFeedHandlers): () => void {
  const interval = setInterval(() => {
    counter += 1;
    const endpoint = endpoints[counter % endpoints.length];
    const roll = counter % 3;

    if (roll === 0) {
      const template = CATEGORIES[counter % CATEGORIES.length];
      handlers.onAlert?.({
        id: `mock-live-alert-${counter}`,
        endpointId: endpoint.id,
        hostname: endpoint.hostname,
        category: template.category,
        severity: template.severity,
        title: template.title,
        description: null,
        status: 'OPEN',
        assignedToUserId: null,
        assignedToUsername: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        resolvedAt: null,
      });
    } else if (roll === 1) {
      const nowOnline = Math.random() > 0.3;
      handlers.onStatus?.({
        endpointId: endpoint.id,
        hostname: endpoint.hostname,
        status: nowOnline ? 'ONLINE' : 'OFFLINE',
        lastHeartbeatAt: new Date().toISOString(),
        labName: endpoint.labName,
      });
    } else {
      const score = Math.floor(Math.random() * 100);
      const level = score >= 80 ? 'CRITICAL' : score >= 60 ? 'HIGH' : score >= 35 ? 'MEDIUM' : score >= 15 ? 'LOW' : 'SAFE';
      handlers.onRisk?.({
        endpointId: endpoint.id,
        score,
        level,
        updatedAt: new Date().toISOString(),
      });
    }
  }, 4000);

  return () => clearInterval(interval);
}
