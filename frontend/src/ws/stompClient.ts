import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { WS_URL } from '../config';
import { tokenStorage } from '../auth/tokenStorage';
import type { AlertResponse, EndpointStatusEvent, RiskScoreResponse } from '../types/api';

export interface LiveFeedHandlers {
  onAlert?: (alert: AlertResponse) => void;
  onStatus?: (event: EndpointStatusEvent) => void;
  onRisk?: (risk: RiskScoreResponse) => void;
  onConnected?: () => void;
  onDisconnected?: () => void;
}

/** Connects to the backend's real STOMP/SockJS endpoint and subscribes to
 * the three dashboard-relevant topics (see WebSocketConfig's Javadoc on
 * the backend for the full topic list — endpoint-scoped topics are used
 * by the endpoint detail page, not this dashboard-level connection).
 * Returns a stop function. */
export function startLiveFeed(handlers: LiveFeedHandlers): () => void {
  const token = tokenStorage.getAccessToken();

  const client = new Client({
    webSocketFactory: () => new SockJS(`${WS_URL}?token=${encodeURIComponent(token ?? '')}`),
    reconnectDelay: 5000, // auto-reconnect — matches "replay should continue automatically after connectivity returns" spirit
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });

  client.onConnect = () => {
    handlers.onConnected?.();

    client.subscribe('/topic/alerts', (message: IMessage) => {
      handlers.onAlert?.(JSON.parse(message.body) as AlertResponse);
    });
    client.subscribe('/topic/endpoints/status', (message: IMessage) => {
      handlers.onStatus?.(JSON.parse(message.body) as EndpointStatusEvent);
    });
    client.subscribe('/topic/risk', (message: IMessage) => {
      handlers.onRisk?.(JSON.parse(message.body) as RiskScoreResponse);
    });
  };

  client.onWebSocketClose = () => handlers.onDisconnected?.();
  client.onStompError = () => handlers.onDisconnected?.();

  client.activate();

  return () => {
    client.deactivate();
  };
}
