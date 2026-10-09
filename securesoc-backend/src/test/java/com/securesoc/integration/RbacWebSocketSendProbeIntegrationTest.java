package com.securesoc.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F7 - security PROBE for client-initiated STOMP SEND frames.
 *
 * WebSocketSubscriptionInterceptor only gates SUBSCRIBE. These tests assert
 * the SECURE expectation - an authenticated client must not be able to
 * inject frames that other users receive - so a FAILURE here is a real
 * finding (see F7 report), not a test defect. They are kept in their own
 * class so a failure cannot mask the RBAC scope results in
 * {@link RbacWebSocketIntegrationTest}.
 */
class RbacWebSocketSendProbeIntegrationTest extends AbstractRbacIntegrationTest {

    private static final long SETTLE_MS = 1500;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Autowired private SimpUserRegistry userRegistry;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    private final List<RbacStompTestClient> opened = new ArrayList<>();

    @AfterEach
    void closeClients() {
        opened.forEach(RbacStompTestClient::close);
        opened.clear();
    }

    private RbacStompTestClient connect(String token, String username, String... destinations) throws Exception {
        RbacStompTestClient client = RbacStompTestClient.connect(
                "http://localhost:" + port + contextPath + "/ws?token=" + token);
        opened.add(client);
        for (String d : destinations) {
            client.subscribe(d);
        }
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            SimpUser user = userRegistry.getUser(username);
            int count = 0;
            if (user != null) {
                for (SimpSession s : user.getSessions()) {
                    count += s.getSubscriptions().size();
                }
            }
            if (count >= destinations.length) {
                break;
            }
            Thread.sleep(50);
        }
        Thread.sleep(300);
        return client;
    }

    /** Faculty SENDs to a shared broker destination; Admin is subscribed to it. */
    @Test
    void clientSend_toSharedTopic_mustNotReachOtherSubscribers() throws Exception {
        RbacStompTestClient admin = connect(adminToken, "rbac_admin", "/topic/probe");
        RbacStompTestClient faculty = connect(facultyFToken, "rbac_faculty_f");

        faculty.send("/topic/probe", Map.of("forged", true));
        Thread.sleep(SETTLE_MS);

        assertEquals(0, admin.received("/topic/probe").size(),
                "client SEND to /topic/** was relayed to another user's subscription");

        // Positive control: the subscription is live and SERVER-side publishing
        // (SimpMessagingTemplate -> brokerChannel) is unaffected by the SEND block.
        messagingTemplate.convertAndSend("/topic/probe", Map.of("server", true));
        Thread.sleep(SETTLE_MS);
        assertEquals(1, admin.received("/topic/probe").size());
        assertEquals(true, admin.received("/topic/probe").get(0).get("server").asBoolean());
    }

    /** Faculty SENDs to ANOTHER user's per-user queue (the same queue the
     * server uses to deliver real alerts); Admin listens on /user/queue/alerts. */
    @Test
    void clientSend_toAnotherUsersQueue_mustNotBeDeliveredAsAnAlert() throws Exception {
        RbacStompTestClient admin = connect(adminToken, "rbac_admin", "/user/queue/alerts");
        RbacStompTestClient faculty = connect(facultyFToken, "rbac_faculty_f");

        faculty.send("/user/rbac_admin/queue/alerts",
                Map.of("id", "00000000-0000-0000-0000-000000000000", "title", "FORGED ALERT",
                        "severity", "CRITICAL", "status", "OPEN"));
        Thread.sleep(SETTLE_MS);

        assertEquals(0, admin.received("/user/queue/alerts").size(),
                "a Faculty client forged an alert into the Admin's /user/queue/alerts");

        // Positive control: a REAL server-side alert event still reaches the Admin
        // (and only that one - never the forged payload).
        assertEquals(HttpStatus.OK,
                post(adminToken, "/alerts/" + alertA.getId() + "/acknowledge", null).getStatusCode());
        Thread.sleep(SETTLE_MS);
        assertEquals(1, admin.received("/user/queue/alerts").size());
        assertEquals(alertA.getId().toString(), admin.received("/user/queue/alerts").get(0).get("id").asText());
    }
}
