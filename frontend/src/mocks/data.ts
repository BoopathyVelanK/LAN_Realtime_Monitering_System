import type {
  AlertResponse, EndpointSummaryResponse, RiskScoreResponse,
  PageResponse, UsbEventResponse, VpnEventResponse, IdleEventResponse,
  NetworkUsageEventResponse, InternetUsageEventResponse, RunningAppSnapshotResponse,
} from '../types/api';

function uuid(seed: string): string {
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
    const online = i % 7 !== 0;
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

// -------------------------------------------------------------------
// Phase 4B — monitoring event mocks. Used by dashboardApi.ts only when
// VITE_USE_MOCKS=true; when false, the same-shaped data comes from the
// real GET /monitoring/** endpoints (MonitoringController). Shapes mirror
// LoginEventResponse/UsbEventResponse/etc. field-for-field, same as the
// generators above mirror EndpointSummaryResponse/AlertResponse.
// -------------------------------------------------------------------

/** Shared by every getXEvents() mock path in dashboardApi.ts - slices an
 * already-generated, already-sorted (newest-first) array the same way
 * the real backend's Pageable does. */
export function paginate<T>(items: T[], page: number, size: number): PageResponse<T> {
  const start = page * size;
  return {
    content: items.slice(start, start + size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  };
}

function pickEndpoint(endpoints: EndpointSummaryResponse[], i: number): EndpointSummaryResponse {
  return endpoints[i % endpoints.length];
}

const USB_DEVICES = [
  { deviceName: 'Cruzer Blade 16GB', vendorId: '0781', productId: '5567' },
  { deviceName: 'DataTraveler 32GB', vendorId: '0951', productId: '1666' },
  { deviceName: 'USB Receiver', vendorId: '046d', productId: 'c52b' },
  { deviceName: 'Mass Storage Device', vendorId: 'ffff', productId: '0000' },
];

export function generateUsbEvents(endpoints: EndpointSummaryResponse[], count = 30): UsbEventResponse[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const e = pickEndpoint(endpoints, i);
    const device = USB_DEVICES[i % USB_DEVICES.length];
    const minutesAgo = i * 9 + Math.floor(Math.random() * 4);
    return {
      id: uuid(`usb-${i}`),
      endpointId: e.id,
      hostname: e.hostname,
      deviceName: device.deviceName,
      deviceId: `${device.vendorId}:${device.productId}`,
      vendorId: device.vendorId,
      productId: device.productId,
      action: i % 2 === 0 ? 'CONNECTED' : 'DISCONNECTED',
      eventTime: new Date(now - minutesAgo * 60_000).toISOString(),
      receivedAt: new Date(now - minutesAgo * 60_000).toISOString(),
    };
  });
}

const VPN_ADAPTERS = ['tun0', 'wg0', 'ppp0', 'NordVPN-tap'];

export function generateVpnEvents(endpoints: EndpointSummaryResponse[], count = 20): VpnEventResponse[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const e = pickEndpoint(endpoints, i);
    const minutesAgo = i * 17 + Math.floor(Math.random() * 6);
    const active = i % 3 !== 0;
    return {
      id: uuid(`vpn-${i}`),
      endpointId: e.id,
      hostname: e.hostname,
      adapterName: active ? VPN_ADAPTERS[i % VPN_ADAPTERS.length] : null,
      active,
      detectedAt: new Date(now - minutesAgo * 60_000).toISOString(),
    };
  });
}

export function generateIdleEvents(endpoints: EndpointSummaryResponse[], count = 50): IdleEventResponse[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const e = pickEndpoint(endpoints, i);
    const minutesAgo = i * 4 + Math.floor(Math.random() * 3);
    const idleSeconds = [0, 30, 120, 900, 2100, 3700][i % 6];
    return {
      id: uuid(`idle-${i}`),
      endpointId: e.id,
      hostname: e.hostname,
      idleSeconds,
      recordedAt: new Date(now - minutesAgo * 60_000).toISOString(),
    };
  });
}

export function generateNetworkUsageEvents(endpoints: EndpointSummaryResponse[], count = 40): NetworkUsageEventResponse[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const e = pickEndpoint(endpoints, i);
    const minutesAgo = (count - i) * 1; // roughly one sample per minute, oldest-to-newest as i increases is reversed below
    return {
      id: uuid(`net-${i}`),
      endpointId: e.id,
      hostname: e.hostname,
      bytesSent: Math.round((20 + Math.sin(i / 3) * 15 + Math.random() * 10) * 1024 * 1024),
      bytesReceived: Math.round((80 + Math.cos(i / 2) * 30 + Math.random() * 20) * 1024 * 1024),
      interfaceName: null,
      recordedAt: new Date(now - minutesAgo * 60_000).toISOString(),
    };
  }).reverse(); // newest first, matching the real endpoint's sort order
}

export function generateInternetUsageEvents(endpoints: EndpointSummaryResponse[], count = 40): InternetUsageEventResponse[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const e = pickEndpoint(endpoints, i);
    const minutesAgo = (count - i) * 1;
    return {
      id: uuid(`inet-${i}`),
      endpointId: e.id,
      hostname: e.hostname,
      uploadMb: Math.round((5 + Math.random() * 15) * 100) / 100,
      downloadMb: Math.round((20 + Math.random() * 60) * 100) / 100,
      periodSeconds: 60,
      recordedAt: new Date(now - minutesAgo * 60_000).toISOString(),
    };
  }).reverse();
}

const PROCESS_NAMES = ['chrome.exe', 'Code.exe', 'python.exe', 'explorer.exe', 'WireGuard.exe', 'uTorrent.exe'];

export function generateRunningAppSnapshots(endpoints: EndpointSummaryResponse[], count = 15): RunningAppSnapshotResponse[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => {
    const e = pickEndpoint(endpoints, i);
    const minutesAgo = i * 20 + Math.floor(Math.random() * 5);
    const appCount = 3 + (i % 4);
    return {
      id: uuid(`snapshot-${i}`),
      endpointId: e.id,
      hostname: e.hostname,
      capturedAt: new Date(now - minutesAgo * 60_000).toISOString(),
      apps: Array.from({ length: appCount }, (_, j) => ({
        processName: PROCESS_NAMES[(i + j) % PROCESS_NAMES.length],
        windowTitle: null,
        pid: 1000 + i * 10 + j,
      })),
    };
  });
}
