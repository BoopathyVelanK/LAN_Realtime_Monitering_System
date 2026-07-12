import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { dashboardApi } from './dashboardApi';
import { queryKeys } from './queryKeys';
import type { EndpointSummaryResponse } from '../types/api';

export function useEndpoints() {
  return useQuery({
    queryKey: queryKeys.endpoints(),
    queryFn: dashboardApi.getEndpoints,
    // WebSocket keeps this fresh in real time; poll as a safety net in case
    // the live connection drops silently.
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
