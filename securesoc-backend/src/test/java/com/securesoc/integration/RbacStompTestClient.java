package com.securesoc.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Minimal real STOMP-over-SockJS client for the F7 WebSocket tests. Connects
 * exactly the way frontend/src/ws/stompClient.ts does (SockJS + ?token=),
 * and records every frame received per destination as parsed JSON.
 */
final class RbacStompTestClient implements AutoCloseable {

    private final WebSocketStompClient stompClient;
    private final StompSession session;
    private final Map<String, List<JsonNode>> received = new ConcurrentHashMap<>();

    private RbacStompTestClient(WebSocketStompClient stompClient, StompSession session) {
        this.stompClient = stompClient;
        this.session = session;
    }

    /** @param sockJsUrl http://localhost:{port}{contextPath}/ws[?token=...] - throws if the handshake is rejected. */
    static RbacStompTestClient connect(String sockJsUrl) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new CompositeMessageConverter(
                List.of(new MappingJackson2MessageConverter(), new StringMessageConverter())));
        try {
            StompSession session = client
                    .connectAsync(sockJsUrl, new WebSocketHttpHeaders(), new StompHeaders(),
                            new StompSessionHandlerAdapter() { })
                    .get(10, TimeUnit.SECONDS);
            return new RbacStompTestClient(client, session);
        } catch (Exception e) {
            client.stop();
            throw e;
        }
    }

    void subscribe(String destination) {
        List<JsonNode> sink = received.computeIfAbsent(destination, d -> new CopyOnWriteArrayList<>());
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                sink.add((JsonNode) payload);
            }
        });
    }

    /** Frames received so far on {@code destination} (live view; never null). */
    List<JsonNode> received(String destination) {
        return received.computeIfAbsent(destination, d -> new CopyOnWriteArrayList<>());
    }

    void send(String destination, Object payload) {
        session.send(destination, payload);
    }

    @Override
    public void close() {
        try {
            if (session.isConnected()) {
                session.disconnect();
            }
        } finally {
            stompClient.stop();
        }
    }
}
