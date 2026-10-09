package com.securesoc.integration;

import com.securesoc.entity.Alert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F7 - REST RBAC end-to-end. Real JWT login, real Spring Security, real
 * PostgreSQL; every assertion is on the RETURNED RECORDS (ids), not just the
 * status code, so "200 OK with another lab's data" cannot pass.
 *
 * Paths and response shapes are taken from the current controllers - nothing
 * is assumed. Detection-rule / IOC / user / role / audit / backup / config
 * administration APIs have no controller in this codebase and therefore
 * cannot be tested here (reported as NOT IMPLEMENTED in the F7 report).
 */
class RbacEndToEndIntegrationTest extends AbstractRbacIntegrationTest {

    static Stream<String> monitoringPaths() {
        return Stream.of("login", "logout", "usb", "vpn", "idle",
                "network-usage", "internet-usage", "running-apps");
    }

    static Stream<String> protectedGetPaths() {
        return Stream.concat(
                Stream.of("/endpoints", "/laboratories", "/departments", "/alerts", "/risk-scores"),
                monitoringPaths().map(p -> "/monitoring/" + p));
    }

    /** Valid bodies on purpose: a malformed body would 400 during argument
     * binding BEFORE @PreAuthorize runs, masking the authorization result. */
    static Stream<Arguments> agentOnlyPosts() {
        return Stream.of(
                Arguments.of("/monitoring/login", Map.of("osUsername", "student1")),
                Arguments.of("/monitoring/logout", Map.of("osUsername", "student1")),
                Arguments.of("/monitoring/running-apps",
                        Map.of("applications", List.of(Map.of("processName", "notepad.exe", "pid", 1)))),
                Arguments.of("/monitoring/usb",
                        Map.of("deviceName", "Flash", "deviceId", "USB1", "vendorId", "1",
                                "productId", "2", "action", "CONNECTED")),
                Arguments.of("/monitoring/vpn", Map.of("adapterName", "tun0", "active", true)),
                Arguments.of("/monitoring/idle", Map.of("idleSeconds", 5)),
                Arguments.of("/monitoring/network-usage", Map.of("bytesSent", 1, "bytesReceived", 2)),
                Arguments.of("/monitoring/internet-usage",
                        Map.of("uploadMb", 1.5, "downloadMb", 2.5, "periodSeconds", 60)),
                Arguments.of("/agents/heartbeat",
                        Map.of("cpuUsagePct", 1.0, "ramUsagePct", 1.0, "diskUsagePct", 1.0,
                                "ipAddress", "10.0.0.1")));
    }

    private String a() { return endpointA.getId().toString(); }
    private String b() { return endpointB.getId().toString(); }

    // ===================================================================
    // Authentication: 401 / valid JWTs accepted
    // ===================================================================

    @ParameterizedTest
    @MethodSource("protectedGetPaths")
    void noJwt_protectedApi_returns401(String path) {
        assertEquals(HttpStatus.UNAUTHORIZED, get(null, path).getStatusCode());
    }

    @Test
    void garbageJwt_returns401() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("not.a.jwt", "/endpoints").getStatusCode());
    }

    @Test
    void adminAndFacultyJwts_areIssuedAndAccepted() {
        for (String token : List.of(adminToken, facultyFToken, facultyZToken)) {
            assertNotNull(token);
            assertFalse(token.isBlank());
            assertEquals(HttpStatus.OK, get(token, "/endpoints").getStatusCode());
        }
    }

    // ===================================================================
    // ADMIN - system-wide
    // ===================================================================

    @Test
    void admin_endpoints_seesBothLabs() {
        ResponseEntity<String> r = get(adminToken, "/endpoints");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(values(json(r), "id").containsAll(Set.of(a(), b())));
    }

    @Test
    void admin_laboratories_seesBothLabs() {
        ResponseEntity<String> r = get(adminToken, "/laboratories");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(values(json(r), "id")
                .containsAll(Set.of(labA.getId().toString(), labB.getId().toString())));
    }

    @Test
    void admin_departments_seesBothDepartments() {
        ResponseEntity<String> r = get(adminToken, "/departments");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(values(json(r), "id")
                .containsAll(Set.of(deptA.getId().toString(), deptB.getId().toString())));
    }

    @Test
    void admin_alerts_seesBothLabsAndEndpointlessAlert() {
        ResponseEntity<String> r = get(adminToken, "/alerts");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(values(json(r), "id").containsAll(Set.of(
                alertA.getId().toString(), alertB.getId().toString(), alertNoEndpoint.getId().toString())));
    }

    @Test
    void admin_riskScores_listAndPerEndpoint() {
        ResponseEntity<String> list = get(adminToken, "/risk-scores");
        assertEquals(HttpStatus.OK, list.getStatusCode());
        assertTrue(values(json(list), "endpointId").containsAll(Set.of(a(), b())));

        assertEquals(HttpStatus.OK, get(adminToken, "/risk-scores/" + a()).getStatusCode());
        assertEquals(HttpStatus.OK, get(adminToken, "/risk-scores/" + b()).getStatusCode());
    }

    @ParameterizedTest
    @MethodSource("monitoringPaths")
    void admin_monitoring_seesBothLabs_andCanFilterToEither(String path) {
        ResponseEntity<String> all = get(adminToken, "/monitoring/" + path);
        assertEquals(HttpStatus.OK, all.getStatusCode());
        assertTrue(pageValues(all, "endpointId").containsAll(Set.of(a(), b())),
                "Admin must see both labs on /monitoring/" + path);

        ResponseEntity<String> onlyB = get(adminToken, "/monitoring/" + path + "?endpointId=" + b());
        assertEquals(HttpStatus.OK, onlyB.getStatusCode());
        assertEquals(Set.of(b()), pageValues(onlyB, "endpointId"));
    }

    // ===================================================================
    // FACULTY F (Lab A only) - allowed scope: Lab A visible, Lab B absent
    // ===================================================================

    @Test
    void facultyF_endpoints_onlyLabA() {
        ResponseEntity<String> r = get(facultyFToken, "/endpoints");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(Set.of(a()), values(json(r), "id"));
    }

    @Test
    void facultyF_laboratories_onlyLabA() {
        ResponseEntity<String> r = get(facultyFToken, "/laboratories");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(Set.of(labA.getId().toString()), values(json(r), "id"));
    }

    @Test
    void facultyF_departments_onlyDeptA() {
        ResponseEntity<String> r = get(facultyFToken, "/departments");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(Set.of(deptA.getId().toString()), values(json(r), "id"));
    }

    @Test
    void facultyF_alerts_onlyLabAAlert_endpointlessAlertHidden() {
        ResponseEntity<String> r = get(facultyFToken, "/alerts");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        // alertNoEndpoint can't be attributed to a lab -> fail closed for Faculty.
        assertEquals(Set.of(alertA.getId().toString()), values(json(r), "id"));

        ResponseEntity<String> open = get(facultyFToken, "/alerts?status=OPEN");
        assertEquals(Set.of(alertA.getId().toString()), values(json(open), "id"));

        ResponseEntity<String> own = get(facultyFToken, "/alerts?endpointId=" + a());
        assertEquals(HttpStatus.OK, own.getStatusCode());
        assertEquals(Set.of(alertA.getId().toString()), values(json(own), "id"));
    }

    @Test
    void facultyF_riskScores_onlyEndpointA() {
        ResponseEntity<String> r = get(facultyFToken, "/risk-scores");
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(Set.of(a()), values(json(r), "endpointId"));

        ResponseEntity<String> one = get(facultyFToken, "/risk-scores/" + a());
        assertEquals(HttpStatus.OK, one.getStatusCode());
        assertEquals(a(), json(one).get("endpointId").asText());
    }

    @ParameterizedTest
    @MethodSource("monitoringPaths")
    void facultyF_monitoring_omittedEndpointId_returnsOnlyLabAData(String path) {
        ResponseEntity<String> r = get(facultyFToken, "/monitoring/" + path);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        // Equality with {A} proves BOTH "Lab A visible" (non-empty) and "Lab B absent".
        assertEquals(Set.of(a()), pageValues(r, "endpointId"),
                "Faculty F must see exactly Lab A data on /monitoring/" + path);
    }

    @ParameterizedTest
    @MethodSource("monitoringPaths")
    void facultyF_monitoring_ownEndpoint_allowed(String path) {
        ResponseEntity<String> r = get(facultyFToken, "/monitoring/" + path + "?endpointId=" + a());
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(Set.of(a()), pageValues(r, "endpointId"));
    }

    // ===================================================================
    // FACULTY F - out-of-scope / direct ID manipulation
    // ===================================================================

    @ParameterizedTest
    @MethodSource("monitoringPaths")
    void facultyF_monitoring_endpointB_returns403(String path) {
        ResponseEntity<String> r = get(facultyFToken, "/monitoring/" + path + "?endpointId=" + b());
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertFalse(String.valueOf(r.getBody()).contains(endpointB.getHostname()),
                "A 403 must not carry Lab B records");
    }

    @ParameterizedTest
    @MethodSource("monitoringPaths")
    void facultyF_monitoring_unknownEndpointId_returns403(String path) {
        // A random (non-existent) id must be treated exactly like an out-of-scope one.
        ResponseEntity<String> r = get(facultyFToken, "/monitoring/" + path + "?endpointId=" + UUID.randomUUID());
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
    }

    @Test
    void facultyF_alertsListWithEndpointB_returns403() {
        assertEquals(HttpStatus.FORBIDDEN, get(facultyFToken, "/alerts?endpointId=" + b()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                get(facultyFToken, "/alerts?endpointId=" + b() + "&status=OPEN").getStatusCode());
    }

    @Test
    void facultyF_riskScoreForEndpointB_returns403_andUnknownEndpointToo() {
        assertEquals(HttpStatus.FORBIDDEN, get(facultyFToken, "/risk-scores/" + b()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get(facultyFToken, "/risk-scores/" + UUID.randomUUID()).getStatusCode());
    }

    @Test
    void facultyF_labBAlert_get_returns403() {
        assertEquals(HttpStatus.FORBIDDEN, get(facultyFToken, "/alerts/" + alertB.getId()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get(facultyFToken, "/alerts/" + alertNoEndpoint.getId()).getStatusCode());
        assertEquals(HttpStatus.OK, get(facultyFToken, "/alerts/" + alertA.getId()).getStatusCode());
    }

    @Test
    void facultyF_labBAlert_acknowledge_returns403_andStateUnchanged() {
        ResponseEntity<String> r = post(facultyFToken, "/alerts/" + alertB.getId() + "/acknowledge", null);
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertAlertUntouched(alertB.getId());

        ResponseEntity<String> endpointless =
                post(facultyFToken, "/alerts/" + alertNoEndpoint.getId() + "/acknowledge", null);
        assertEquals(HttpStatus.FORBIDDEN, endpointless.getStatusCode());
        assertAlertUntouched(alertNoEndpoint.getId());
    }

    @Test
    void facultyF_labBAlert_resolve_returns403_andStateUnchanged() {
        ResponseEntity<String> r = post(facultyFToken, "/alerts/" + alertB.getId() + "/resolve", null);
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertAlertUntouched(alertB.getId());
    }

    @Test
    void facultyF_labAAlert_acknowledge_allowed_positiveControl() {
        ResponseEntity<String> r = post(facultyFToken, "/alerts/" + alertA.getId() + "/acknowledge", null);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals("ACKNOWLEDGED", json(r).get("status").asText());
        assertEquals(Alert.Status.ACKNOWLEDGED,
                alertRepository.findById(alertA.getId()).orElseThrow().getStatus());
        // ...and Lab B's alert is still untouched by the same caller.
        assertAlertUntouched(alertB.getId());
    }

    // ===================================================================
    // FACULTY Z (zero assignments) - must fail closed, never global
    // ===================================================================

    @Test
    void facultyZ_scopedLists_areEmpty_notGlobal() {
        for (String path : List.of("/endpoints", "/laboratories", "/departments", "/alerts", "/risk-scores")) {
            ResponseEntity<String> r = get(facultyZToken, path);
            assertEquals(HttpStatus.OK, r.getStatusCode(), path);
            assertTrue(json(r).isArray(), path);
            assertEquals(0, json(r).size(), "Zero assignments must yield an EMPTY list for " + path);
        }
    }

    @ParameterizedTest
    @MethodSource("monitoringPaths")
    void facultyZ_monitoring_omittedEndpointId_isEmpty(String path) {
        ResponseEntity<String> r = get(facultyZToken, "/monitoring/" + path);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(0, json(r).get("content").size(), "Zero assignments must not fall back to findAll");
        assertEquals(0, json(r).get("totalElements").asLong());
    }

    @ParameterizedTest
    @MethodSource("monitoringPaths")
    void facultyZ_monitoring_anyExplicitEndpoint_returns403(String path) {
        assertEquals(HttpStatus.FORBIDDEN,
                get(facultyZToken, "/monitoring/" + path + "?endpointId=" + a()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                get(facultyZToken, "/monitoring/" + path + "?endpointId=" + b()).getStatusCode());
    }

    @Test
    void facultyZ_resourceSpecificAccess_returns403() {
        assertEquals(HttpStatus.FORBIDDEN, get(facultyZToken, "/alerts?endpointId=" + a()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get(facultyZToken, "/risk-scores/" + a()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get(facultyZToken, "/alerts/" + alertA.getId()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                post(facultyZToken, "/alerts/" + alertA.getId() + "/acknowledge", null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                post(facultyZToken, "/alerts/" + alertA.getId() + "/resolve", null).getStatusCode());
        assertAlertUntouched(alertA.getId());
    }

    // ===================================================================
    // Role boundaries
    // ===================================================================

    @ParameterizedTest
    @MethodSource("agentOnlyPosts")
    void facultyJwt_cannotCallAgentOnlyPost_returns403_andWritesNothing(String path, Object body) {
        long before = monitoringRowCount();
        assertEquals(HttpStatus.FORBIDDEN, post(facultyFToken, path, body).getStatusCode());
        assertEquals(before, monitoringRowCount(), "A denied POST must not persist anything");
    }

    @ParameterizedTest
    @MethodSource("agentOnlyPosts")
    void adminJwt_cannotCallAgentOnlyPost_returns403(String path, Object body) {
        long before = monitoringRowCount();
        assertEquals(HttpStatus.FORBIDDEN, post(adminToken, path, body).getStatusCode());
        assertEquals(before, monitoringRowCount());
    }

    @Test
    void agentToken_positiveControl_canPostOwnMonitoringEvent() {
        long before = idleEventRepository.count();
        ResponseEntity<String> r = postWithAgentToken(agentTokenA, "/monitoring/idle", Map.of("idleSeconds", 5));
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(before + 1, idleEventRepository.count());
    }

    /** Agents authenticate with an opaque X-Agent-Token, not a JWT. On
     * /monitoring/** the token is honoured (ROLE_AGENT) - which hasAnyRole('ADMIN','FACULTY') then denies. */
    @ParameterizedTest
    @MethodSource("monitoringPaths")
    void agentToken_cannotReadMonitoringData_returns403(String path) {
        assertEquals(HttpStatus.FORBIDDEN, getWithAgentToken(agentTokenA, "/monitoring/" + path).getStatusCode());
    }

    /** AgentTokenAuthFilter only runs on /agents/heartbeat and /monitoring/**,
     * so on every other user-facing API the agent token is simply not an
     * identity at all -> 401 (not 403). */
    @ParameterizedTest
    @MethodSource("userApiPaths")
    void agentToken_isNotAnIdentityOnUserApis_returns401(String path) {
        assertEquals(HttpStatus.UNAUTHORIZED, getWithAgentToken(agentTokenA, path).getStatusCode());
    }

    static Stream<String> userApiPaths() {
        return Stream.of("/endpoints", "/laboratories", "/departments", "/alerts", "/risk-scores");
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private void assertAlertUntouched(UUID alertId) {
        Alert reloaded = alertRepository.findById(alertId).orElseThrow();
        assertEquals(Alert.Status.OPEN, reloaded.getStatus(), "denied mutation must not change status");
        assertNull(reloaded.getAcknowledgedAt());
        assertNull(reloaded.getResolvedAt());
    }
}
