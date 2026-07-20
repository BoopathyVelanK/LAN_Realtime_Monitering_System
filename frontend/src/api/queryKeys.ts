import type { MonitoringListParams } from '../types/api';

/**
 * Central query-key factory for TanStack Query. Every hook in queries.ts
 * sources its queryKey from here so cache lookups/invalidations stay in
 * one place instead of scattered string literals across hooks.
 */

function monitoringKey(resource: string, params?: MonitoringListParams) {
  return ['monitoring', resource, params ?? {}] as const;
}

export const queryKeys = {
  endpoints: () => ['endpoints'] as const,
  alerts: () => ['alerts'] as const,
  riskScores: () => ['riskScores'] as const,

  // Phase 4B - monitoring event feeds (GET /monitoring/**). See
  // dashboardApi.ts for which of these have a real backend behind them.
  usbEvents: (params?: MonitoringListParams) => monitoringKey('usb', params),
  vpnEvents: (params?: MonitoringListParams) => monitoringKey('vpn', params),
  idleEvents: (params?: MonitoringListParams) => monitoringKey('idle', params),
  networkUsageEvents: (params?: MonitoringListParams) => monitoringKey('network-usage', params),
  internetUsageEvents: (params?: MonitoringListParams) => monitoringKey('internet-usage', params),
  runningAppSnapshots: (params?: MonitoringListParams) => monitoringKey('running-apps', params),

  // Departments / Laboratories (GET /departments, GET /laboratories) -
  // real backend, see dashboardApi.ts.
  departments: () => ['departments'] as const,
  laboratories: () => ['laboratories'] as const,
};
