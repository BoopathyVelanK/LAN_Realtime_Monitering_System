import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { dashboardApi } from './dashboardApi';
import { queryKeys } from './queryKeys';
import type { EndpointSummaryResponse, MonitoringListParams } from '../types/api';

export function useEndpoints() {
  return useQuery({
    queryKey: queryKeys.endpoints(),
    queryFn: dashboardApi.getEndpoints,
    refetchInterval: 60_000,
  });
}

export function useAlerts() {
  return useQuery({
    queryKey: queryKeys.alerts(),
    queryFn: () => dashboardApi.getAlerts(),
    refetchInterval: 60_000,
  });
}

export function useRiskScores(endpoints: EndpointSummaryResponse[] | undefined) {
  return useQuery({
    queryKey: queryKeys.riskScores(),
    queryFn: () => dashboardApi.getAllRiskScores(endpoints ?? []),
    enabled: !!endpoints && endpoints.length > 0,
    refetchInterval: 60_000,
  });
}

export function useAcknowledgeAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => dashboardApi.acknowledgeAlert(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.alerts() }),
  });
}

export function useResolveAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => dashboardApi.resolveAlert(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.alerts() }),
  });
}

// -----------------------------------------------------------------------
// Phase 4B - monitoring event feeds. Real backend (MonitoringController's
// GET endpoints) when VITE_USE_MOCKS=false, see dashboardApi.ts header.
// -----------------------------------------------------------------------

export function useUsbEvents(params?: MonitoringListParams) {
  return useQuery({
    queryKey: queryKeys.usbEvents(params),
    queryFn: () => dashboardApi.getUsbEvents(params),
    refetchInterval: 30_000,
  });
}

export function useVpnEvents(params?: MonitoringListParams) {
  return useQuery({
    queryKey: queryKeys.vpnEvents(params),
    queryFn: () => dashboardApi.getVpnEvents(params),
    refetchInterval: 30_000,
  });
}

export function useIdleEvents(params?: MonitoringListParams) {
  return useQuery({
    queryKey: queryKeys.idleEvents(params),
    queryFn: () => dashboardApi.getIdleEvents(params),
    refetchInterval: 30_000,
  });
}

export function useNetworkUsageEvents(params?: MonitoringListParams) {
  return useQuery({
    queryKey: queryKeys.networkUsageEvents(params),
    queryFn: () => dashboardApi.getNetworkUsageEvents(params),
    refetchInterval: 15_000,
  });
}

export function useInternetUsageEvents(params?: MonitoringListParams) {
  return useQuery({
    queryKey: queryKeys.internetUsageEvents(params),
    queryFn: () => dashboardApi.getInternetUsageEvents(params),
    refetchInterval: 30_000,
  });
}

export function useRunningAppSnapshots(params?: MonitoringListParams) {
  return useQuery({
    queryKey: queryKeys.runningAppSnapshots(params),
    queryFn: () => dashboardApi.getRunningAppSnapshots(params),
    refetchInterval: 30_000,
  });
}
