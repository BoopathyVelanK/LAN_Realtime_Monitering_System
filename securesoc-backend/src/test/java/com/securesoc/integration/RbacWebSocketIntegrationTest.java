package com.securesoc.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.securesoc.dto.AlertResponse;
import com.securesoc.dto.RiskScoreResponse;
import com.securesoc.service.EndpointEventPublisher;
import com.securesoc.service.WebSocketAlertEventPublisher;
import com.securesoc.service.WebSocketRiskEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F7 - WebSocket RBAC end-to-end over a real STOMP/SockJS connection.
 *
 * Destinations under test are the CURRENT per-user queues
 * (/user/queue/alerts, /user/queue/endpoints/status, /user/queue/risk) - the
 * old /topic/* broadcast destinations no longer exist.
 *
 * Alert events are triggered through the REAL REST path (Admin acknowledges
 * an alert -> AlertService -> WebSocketAlertEventPublisher after commit).
 * Risk / endpoint-status / null-endpointId events are fired through the real
 * publisher beans, since their production triggers (detection engine,
 * heartbeat sweeper) are covered elsewhere and are not the subject here -
 * the subject is WHO receives the event.
 *
 * Timing: positive expectations poll up to 5s; "nothing more arrives" is
 * checked after a fixed settle window once the expected frames are in. The
 * Admin queue is always awaited first, so by the time Faculty queues are
 * asserted, delivery of every event has already completed.
 */
class RbacWebSocketIntegrationTest extends AbstractRbacIntegrationTest {

    private static final String ALERTS = "/user/queue/alerts";
    private static final String STATUS = "/user/queue/endpoints/status";
    private static final String RISK = "/user/queue/risk";
    private static final long SETTLE_MS = 800;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Autowired private SimpUserRegistry userRegistry;
    @Autowired private WebSocketAlertEventPublisher alertPublisher;
    @Autowired private WebSocketRiskEventPublisher riskPublisher;
    @Autowired private EndpointEventPublisher endpointEventPublisher;

    private final List<RbacStompTestClient> opened = new ArrayList<>();

    @AfterEach
    void closeClients() {
        opened.forEach(RbacStompTestClient::close);
        opened.clear();
    }

    private String wsUrl(String token) {
        return "http://localhost:" + port + contextPath + "/ws" + (token == null ? "" : "?token=" + token);
    }

    private RbacStompTestClient connectAndSubscribe(String token, String username) throws Exception {
        RbacStompTestClient client = RbacStompTestClient.connect(wsUrl(token));
        opened.add(client);
        client.subscribe(ALERTS);
        client.subscribe(STATUS);
        client.subscribe(RISK);
        awaitSubscriptions(username, 3);
        return client;
    }

    /** Waits until the broker's user registry shows the expected number of
     * subscriptions for the user, then a short grace so the broker's own
     * (asynchronous) registration has certainly finished too. */
    private void awaitSubscriptions(String username, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            SimpUser user = userRegistry.getUser(username);
            if (user != null) {
                int count = 0;
                for (SimpSession s : user.getSessions()) {
                    count += s.getSubscriptions().size();
                }
                if (count >= expected) {
                    Thread.sleep(300);
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Subscriptions for " + username + " never registered");
    }

    private static void awaitExactly(List<JsonNode> frames, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (frames.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Thread.sleep(SETTLE_MS);
        assertEquals(expected, frames.size(), "unexpected number of frames: " + frames);
    }

    private static Set<String> endpointIds(List<JsonNode> frames) {
        Set<String> out = new HashSet<>();
        for (JsonNode f : frames) {
            out.add(f.get("endpointId").asText());
        }
        return out;
    }

    private AlertResponse alertEvent(UUID endpointId) {
        return new AlertResponse(UUID.randomUUID(), endpointId, null, "TEST", "HIGH", "forced", null,
                "OPEN", null, null, null, null, null, Instant.now(), Instant.now(), null);
    }

    // ===================================================================

    @Test
    void alerts_adminSystemWide_facultyScoped_zeroAssignmentsGetNothing() throws Exception {
        RbacStompTestClient admin = connectAndSubscribe(adminToken, "rbac_admin");
        RbacStompTestClient f = connectAndSubscribe(facultyFToken, "rbac_faculty_f");
        RbacStompTestClient z = connectAndSubscribe(facultyZToken, "rbac_faculty_z");

        // Real REST trigger: Lab B first, then Lab A.
        assertEquals(HttpStatus.OK,
                post(adminToken, "/alerts/" + alertB.getId() + "/acknowledge", null).getStatusCode());
        assertEquals(HttpStatus.OK,
                post(adminToken, "/alerts/" + alertA.getId() + "/acknowledge", null).getStatusCode());
        // endpointId == null event: Admin only.
        alertPublisher.publishAlert(alertEvent(null));

        awaitExactly(admin.received(ALERTS), 3);
        assertEquals(Set.of(endpointA.getId().toString(), endpointB.getId().toString(), "null"),
                endpointIds(admin.received(ALERTS)));

        // Faculty F: ONLY the Lab A alert; nothing from Lab B, nothing endpoint-less.
        awaitExactly(f.received(ALERTS), 1);
        JsonNode only = f.received(ALERTS).get(0);
        assertEquals(alertA.getId().toString(), only.get("id").asText());
        assertEquals(endpointA.getId().toString(), only.get("endpointId").asText());
        assertFalse(f.received(ALERTS).toString().contains(endpointB.getId().toString()));
        assertFalse(f.received(ALERTS).toString().contains(endpointB.getHostname()));

        // Faculty Z (zero assignments): nothing.
        awaitExactly(z.received(ALERTS), 0);
    }

    @Test
    void riskScores_adminSystemWide_facultyScoped_zeroAssignmentsGetNothing() throws Exception {
        RbacStompTestClient admin = connectAndSubscribe(adminToken, "rbac_admin");
        RbacStompTestClient f = connectAndSubscribe(facultyFToken, "rbac_faculty_f");
        RbacStompTestClient z = connectAndSubscribe(facultyZToken, "rbac_faculty_z");

        riskPublisher.publishRiskScore(new RiskScoreResponse(endpointB.getId(), (short) 90, "CRITICAL", Instant.now()));
        riskPublisher.publishRiskScore(new RiskScoreResponse(endpointA.getId(), (short) 40, "HIGH", Instant.now()));
        riskPublisher.publishRiskScore(new RiskScoreResponse(null, (short) 10, "LOW", Instant.now()));

        awaitExactly(admin.received(RISK), 3);
        assertEquals(Set.of(endpointA.getId().toString(), endpointB.getId().toString(), "null"),
                endpointIds(admin.received(RISK)));

        awaitExactly(f.received(RISK), 1);
        assertEquals(Set.of(endpointA.getId().toString()), endpointIds(f.received(RISK)));

        awaitExactly(z.received(RISK), 0);
    }

    @Test
    void endpointStatus_adminSystemWide_facultyScoped_zeroAssignmentsGetNothing() throws Exception {
        RbacStompTestClient admin = connectAndSubscribe(adminToken, "rbac_admin");
        RbacStompTestClient f = connectAndSubscribe(facultyFToken, "rbac_faculty_f");
        RbacStompTestClient z = connectAndSubscribe(facultyZToken, "rbac_faculty_z");

        endpointEventPublisher.publishStatusChange(endpointB);
        endpointEventPublisher.publishStatusChange(endpointA);

        awaitExactly(admin.received(STATUS), 2);
        assertEquals(Set.of(endpointA.getId().toString(), endpointB.getId().toString()),
                endpointIds(admin.received(STATUS)));

        awaitExactly(f.received(STATUS), 1);
        assertEquals(Set.of(endpointA.getId().toString()), endpointIds(f.received(STATUS)));
        assertFalse(f.received(STATUS).toString().contains(labB.getName()));

        awaitExactly(z.received(STATUS), 0);
    }

    /** The retired shared broadcast topics must stay dead: a Faculty client
     * subscribed to them receives nothing while events flow to others. */
    @Test
    void legacyTopicDestinations_receiveNothing() throws Exception {
        RbacStompTestClient admin = connectAndSubscribe(adminToken, "rbac_admin");
        RbacStompTestClient f = RbacStompTestClient.connect(wsUrl(facultyFToken));
        opened.add(f);
        f.subscribe("/topic/alerts");
        f.subscribe("/topic/risk");
        f.subscribe("/topic/endpoints/status");
        awaitSubscriptions("rbac_faculty_f", 3);

        post(adminToken, "/alerts/" + alertB.getId() + "/acknowledge", null);
        riskPublisher.publishRiskScore(new RiskScoreResponse(endpointB.getId(), (short) 90, "CRITICAL", Instant.now()));
        endpointEventPublisher.publishStatusChange(endpointB);

        awaitExactly(admin.received(ALERTS), 1);
        awaitExactly(f.received("/topic/alerts"), 0);
        awaitExactly(f.received("/topic/risk"), 0);
        awaitExactly(f.received("/topic/endpoints/status"), 0);
    }

    @Test
    void handshake_withoutToken_isRejected() {
        assertThrows(Exception.class, () -> opened.add(RbacStompTestClient.connect(wsUrl(null))));
    }

    @Test
    void handshake_withGarbageToken_isRejected() {
        assertThrows(Exception.class, () -> opened.add(RbacStompTestClient.connect(wsUrl("not.a.jwt"))));
    }

    @Test
    void handshake_withValidTokens_isAccepted() throws Exception {
        for (String token : List.of(adminToken, facultyFToken, facultyZToken)) {
            RbacStompTestClient c = RbacStompTestClient.connect(wsUrl(token));
            opened.add(c);
        }
        assertTrue(opened.size() == 3);
    }
}
