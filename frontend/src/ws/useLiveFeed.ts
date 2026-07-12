import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { USE_MOCKS } from '../config';
import { startLiveFeed } from './stompClient';
import { startMockLiveFeed } from '../mocks/mockLiveFeed';
import { queryKeys } from '../api/queryKeys';
import type { AlertResponse, EndpointSummaryResponse, EndpointStatusEvent, RiskScoreResponse } from '../types/api';

const MAX_LIVE_ALERTS = 30;

export interface ConnectionState {
  connected: boolean;
  recentAlerts: AlertResponse[];
}

/** Subscribes to live alert/status/risk events (real WebSocket, or the
 * mock emitter when USE_MOCKS is on) and writes them straight into React
 * Query's cache, so every component reading useEndpoints/useAlerts/
 * useRiskScores re-renders live without any polling. Also keeps a small
 * rolling buffer of the most recent alerts for the live feed panel. */
export function useLiveFeed(): ConnectionState {
  const queryClient = useQueryClient();
  const [connected, setConnected] = useState(false);
  const [recentAlerts, setRecentAlerts] = useState<AlertResponse[]>([]);
  const startedRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) return; // guard against React 18/19 strict-mode double-invoke in dev
    startedRef.current = true;

    const handleAlert = (alert: AlertResponse) => {
      setRecentAlerts((prev) => [alert, ...prev].slice(0, MAX_LIVE_ALERTS));
      queryClient.setQueryData<AlertResponse[]>(queryKeys.alerts(), (prev) => {
        if (!prev) return prev;
        const idx = prev.findIndex((a) => a.id === alert.id);
        if (idx === -1) return [alert, ...prev];
        const next = [...prev];
        next[idx] = alert;
        return next;
      });
    };

    const handleStatus = (event: EndpointStatusEvent) => {
      queryClient.setQueryData<EndpointSummaryResponse[]>(queryKeys.endpoints(), (prev) => {
        if (!prev) return prev;
        return prev.map((e) => (e.id === event.endpointId ? { ...e, status: event.status, lastHeartbeatAt: event.lastHeartbeatAt } : e));
      });
    };

    const handleRisk = (risk: RiskScoreResponse) => {
      queryClient.setQueryData<RiskScoreResponse[]>(queryKeys.riskScores(), (prev) => {
        if (!prev) return [risk];
        const idx = prev.findIndex((r) => r.endpointId === risk.endpointId);
        if (idx === -1) return [...prev, risk];
        const next = [...prev];
        next[idx] = risk;
        return next;
      });
    };

    if (USE_MOCKS) {
      setConnected(true);
      const stop = startMockLiveFeed({ onAlert: handleAlert, onStatus: handleStatus, onRisk: handleRisk });
      return () => {
        stop();
        startedRef.current = false;
      };
    }

    const stop = startLiveFeed({
      onAlert: handleAlert,
      onStatus: handleStatus,
      onRisk: handleRisk,
      onConnected: () => setConnected(true),
      onDisconnected: () => setConnected(false),
    });
    return () => {
      stop();
      startedRef.current = false;
    };
  }, [queryClient]);

  return { connected, recentAlerts };
}
