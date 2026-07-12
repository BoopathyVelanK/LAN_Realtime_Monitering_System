import type { AlertResponse, EndpointSummaryResponse, RiskScoreResponse } from '../types/api';

function uuid(seed: string): string {
  // Deterministic-looking fake UUIDs so mock data stays stable across renders.
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  const hex = h.toString(16).padStart(8, '0');
  return `${hex}-${seed.length.toString(16).padStart(4, '0')}-4${hex.slice(0, 3)}-8${hex.slice(3, 6)}-${hex.repeat(2).slice(0, 12)}`;
}

const LABS = [
  { id: uuid('lab-a'), name: 'Lab A — Networking' },
  { id: uuid('lab-b'), name: 'Lab B — Systems' },
  { id: uuid('lab-c'), name: 'Lab C — Security' },
];

const OS_OPTIONS = [
  { osName: 'Windows 11 Pro', osVersion: '23H2' },
  { osName: 'Windows 10 Pro', osVersion: '22H2' },
];

function macFor(i: number): string {
  const hex = i.toString(16).padStart(2, '0');
  return `AC:DE:48:00:11:${hex}`;
}

export function generateEndpoints(count = 24): EndpointSummaryResponse[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const lab = LABS[i % LABS.length];
    const os = OS_OPTIONS[i % OS_OPTIONS.length];
    const online = i % 7 !== 0; // most online, a handful offline
    const minutesAgo = online ? Math.floor(Math.random() * 2) : 15 + Math.floor(Math.random() * 500);
    return {
      id: uuid(`endpoint-${i}`),
      hostname: `${lab.name.slice(0, 5).toUpperCase().replace(/[^A-Z]/g, '')}-PC-${String(i + 1).padStart(2, '0')}`,
      macAddress: macFor(i),
      ipAddress: `10.20.${(i % 4) + 1}.${10 + i}`,
      labId: lab.id,
      labName: lab.name,
      status: online ? 'ONLINE' : 'OFFLINE',
      lastHeartbeatAt: new Date(now - minutesAgo * 60_000).toISOString(),
      osName: os.osName,
      osVersion: os.osVersion,
      cpuInfo: 'Intel Core i5-12400',
      ramMb: 16384,
      diskGb: 512,
      agentVersion: '0.1.0-PHASE2',
    };
  });
}

const ALERT_TEMPLATES: Array<{ category: string; severity: AlertResponse['severity']; title: string }> = [
  { category: 'USB', severity: 'MEDIUM', title: 'Unrecognized USB storage device inserted' },
  { category: 'AUTH', severity: 'HIGH', title: 'Repeated failed login attempts' },
  { category: 'PROCESS', severity: 'CRITICAL', title: 'Suspicious PowerShell execution detected' },
  { category: 'VPN', severity: 'LOW', title: 'VPN adapter activated outside policy hours' },
  { category: 'IDLE', severity: 'INFO', title: 'Endpoint idle beyond configured threshold' },
  { category: 'NETWORK', severity: 'MEDIUM', title: 'Unusual outbound network volume' },
];

export function generateAlerts(endpoints: EndpointSummaryResponse[], count = 14): AlertResponse[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const endpoint = endpoints[i % endpoints.length];
    const template = ALERT_TEMPLATES[i % ALERT_TEMPLATES.length];
    const minutesAgo = i * 6 + Math.floor(Math.random() * 5);
    const resolved = i % 5 === 0;
    return {
      id: uuid(`alert-${i}`),
      endpointId: endpoint.id,
      hostname: endpoint.hostname,
      category: template.category,
      severity: template.severity,
      title: template.title,
      description: null,
      status: resolved ? 'RESOLVED' : i % 3 === 0 ? 'ACKNOWLEDGED' : 'OPEN',
      assignedToUserId: null,
      assignedToUsername: null,
      createdAt: new Date(now - minutesAgo * 60_000).toISOString(),
      updatedAt: new Date(now - minutesAgo * 60_000).toISOString(),
      resolvedAt: resolved ? new Date(now - (minutesAgo - 2) * 60_000).toISOString() : null,
    };
  });
}

export function generateRiskScores(endpoints: EndpointSummaryResponse[]): RiskScoreResponse[] {
  return endpoints.map((e, i) => {
    const score = i % 7 === 0 ? 70 + (i % 30) : i % 5 === 0 ? 40 + (i % 20) : Math.floor(Math.random() * 25);
    const level = score >= 80 ? 'CRITICAL' : score >= 60 ? 'HIGH' : score >= 35 ? 'MEDIUM' : score >= 15 ? 'LOW' : 'SAFE';
    return {
      endpointId: e.id,
      score,
      level,
      updatedAt: new Date().toISOString(),
    };
  });
}
