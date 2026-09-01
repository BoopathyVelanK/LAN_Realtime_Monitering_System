package com.securesoc.service;

import com.securesoc.detection.DetectionResult;
import com.securesoc.entity.Alert;
import com.securesoc.entity.DetectionRule;
import com.securesoc.entity.EndpointDevice;
import com.securesoc.entity.User;
import com.securesoc.exception.ResourceNotFoundException;
import com.securesoc.repository.AlertRepository;
import com.securesoc.repository.DetectionRuleRepository;
import com.securesoc.repository.EndpointDeviceRepository;
import com.securesoc.dto.AlertResponse;
import com.securesoc.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;
    @Mock
    private DetectionRuleRepository detectionRuleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EndpointDeviceRepository endpointDeviceRepository;
    @Mock
    private AlertInsertExecutor alertInsertExecutor;
    @Mock
    private WebSocketAlertEventPublisher alertPublisher;

    private AlertService alertService;

    private UUID ruleId;
    private UUID userId;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(
            alertRepository, detectionRuleRepository, userRepository, endpointDeviceRepository,
            alertInsertExecutor, alertPublisher);
        ruleId = UUID.randomUUID();
        userId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
    }

    private DetectionRule rule() {
        return rule(ruleId);
    }

    private DetectionRule rule(UUID id) {
        DetectionRule rule = new DetectionRule();
        rule.setId(id);
        rule.setName("Repeated failed login");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("AUTH_FAILURE");
        rule.setSeverity(DetectionRule.Severity.HIGH);
        return rule;
    }

    private DetectionResult detectedResult(UUID userId, UUID endpointId) {
        return detectedResult(ruleId, userId, endpointId);
    }

    private DetectionResult detectedResult(UUID ruleId, UUID userId, UUID endpointId) {
        return new DetectionResult(
            true,
            ruleId,
            DetectionRule.Severity.HIGH,
            "Repeated failed login attempts detected",
            "5 failed login attempts observed for user within the last 300 seconds (threshold: 5).",
            userId,
            endpointId
        );
    }

    // --- detected result creates and saves an Alert (no user - dedup skipped) ---

    @Test
    void createAlertFrom_detectedResult_savesAlert() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Alert> result = alertService.createAlertFrom(detectedResult(null, null));

        assertTrue(result.isPresent());
        verify(alertRepository, times(1)).save(any(Alert.class));
        assertEquals("Repeated failed login attempts detected", result.get().getTitle());
        assertEquals(Alert.Severity.HIGH, result.get().getSeverity());
    }

    // --- DetectionResult.none() does not save an Alert ---

    @Test
    void createAlertFrom_none_doesNotSaveAlert() {
        Optional<Alert> result = alertService.createAlertFrom(DetectionResult.none());

        assertTrue(result.isEmpty());
        verifyNoInteractions(alertRepository, detectionRuleRepository, userRepository,
            endpointDeviceRepository, alertInsertExecutor, alertPublisher);
    }

    @Test
    void createAlertFrom_notDetected_doesNotSaveAlert() {
        DetectionResult notDetected = new DetectionResult(
            false, ruleId, DetectionRule.Severity.HIGH, "t", "d", userId, endpointId);

        Optional<Alert> result = alertService.createAlertFrom(notDetected);

        assertTrue(result.isEmpty());
        verifyNoInteractions(alertRepository, detectionRuleRepository, userRepository,
            endpointDeviceRepository, alertInsertExecutor, alertPublisher);
    }

    @Test
    void createAlertFrom_null_doesNotSaveAlert() {
        Optional<Alert> result = alertService.createAlertFrom(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(alertRepository, detectionRuleRepository, userRepository,
            endpointDeviceRepository, alertInsertExecutor, alertPublisher);
    }

    // --- userId present: resolved, mapped, and routed through the dedup path ---

    @Test
    void createAlertFrom_userIdPresent_mapsUser() {
        User user = new User();
        user.setId(userId);
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN))
            .thenReturn(Optional.empty());
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        when(alertInsertExecutor.insertAlert(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(userId, null));

        assertEquals(user, captor.getValue().getUser());
        verify(userRepository).findById(userId);
    }

    @Test
    void createAlertFrom_userIdAbsent_userNotResolved() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(null, null));

        verifyNoInteractions(userRepository);
    }

    // --- endpointId is mapped when present (userId absent here, so still the direct-save path) ---

    @Test
    void createAlertFrom_endpointIdPresent_mapsEndpoint() {
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setId(endpointId);
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        when(alertRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(null, endpointId));

        assertEquals(endpoint, captor.getValue().getEndpoint());
        verify(endpointDeviceRepository).findById(endpointId);
    }

    @Test
    void createAlertFrom_endpointIdAbsent_endpointNotResolved() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(null, null));

        verifyNoInteractions(endpointDeviceRepository);
    }

    // --- repository references are resolved correctly ---

    @Test
    void createAlertFrom_resolvesRuleReferenceThroughRepository() {
        DetectionRule rule = rule();
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        when(alertRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(null, null));

        assertEquals(rule, captor.getValue().getRule());
        verify(detectionRuleRepository).findById(ruleId);
    }

    @Test
    void createAlertFrom_resolvesUserAndEndpointThroughRepositories() {
        User user = new User();
        user.setId(userId);
        EndpointDevice endpoint = new EndpointDevice();
        endpoint.setId(endpointId);
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.of(endpoint));
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN))
            .thenReturn(Optional.empty());
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        when(alertInsertExecutor.insertAlert(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(userId, endpointId));

        assertEquals(user, captor.getValue().getUser());
        assertEquals(endpoint, captor.getValue().getEndpoint());
        verify(userRepository).findById(userId);
        verify(endpointDeviceRepository).findById(endpointId);
        verify(alertInsertExecutor).insertAlert(any(Alert.class));
        verify(alertRepository, never()).save(any(Alert.class));
    }

    // --- missing required DetectionRule/User/EndpointDevice is handled correctly ---

    @Test
    void createAlertFrom_missingRule_throwsResourceNotFoundExceptionAndDoesNotSave() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> alertService.createAlertFrom(detectedResult(null, null)));

        verify(alertRepository, never()).save(any(Alert.class));
        verifyNoInteractions(alertInsertExecutor, alertPublisher);
    }

    @Test
    void createAlertFrom_missingUser_throwsResourceNotFoundExceptionAndDoesNotSave() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> alertService.createAlertFrom(detectedResult(userId, null)));

        verify(alertRepository, never()).save(any(Alert.class));
        verifyNoInteractions(alertInsertExecutor, alertPublisher);
    }

    @Test
    void createAlertFrom_missingEndpoint_throwsResourceNotFoundExceptionAndDoesNotSave() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> alertService.createAlertFrom(detectedResult(null, endpointId)));

        verify(alertRepository, never()).save(any(Alert.class));
        verifyNoInteractions(alertInsertExecutor, alertPublisher);
    }

    // =====================================================================
    // Alert deduplication
    // =====================================================================

    // --- first detection for a (user, rule) pair creates a new alert ---

    @Test
    void createAlertFrom_firstDetectionForUserAndRule_createsNewAlert() {
        User user = new User();
        user.setId(userId);
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN))
            .thenReturn(Optional.empty());
        Alert inserted = new Alert();
        inserted.setUser(user);
        when(alertInsertExecutor.insertAlert(any(Alert.class))).thenReturn(inserted);

        Optional<Alert> result = alertService.createAlertFrom(detectedResult(userId, null));

        assertTrue(result.isPresent());
        assertSame(inserted, result.get());
        verify(alertInsertExecutor).insertAlert(any(Alert.class));
        verify(alertRepository, never()).save(any(Alert.class));
    }

    // --- duplicate OPEN detection reuses the existing alert instead of inserting ---

    @Test
    void createAlertFrom_existingOpenAlertForSameUserAndRule_reusesExistingAlert() {
        User user = new User();
        user.setId(userId);
        Alert existing = new Alert();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setStatus(Alert.Status.OPEN);

        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN))
            .thenReturn(Optional.of(existing));

        Optional<Alert> result = alertService.createAlertFrom(detectedResult(userId, null));

        assertTrue(result.isPresent());
        assertSame(existing, result.get());
        verifyNoInteractions(alertInsertExecutor, alertPublisher);
        verify(alertRepository, never()).save(any(Alert.class));
    }

    // --- an ACKNOWLEDGED existing alert does not suppress a new OPEN alert ---

    @Test
    void createAlertFrom_existingAcknowledgedAlert_doesNotSuppressNewOpenAlert() {
        User user = new User();
        user.setId(userId);
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // The dedup lookup only ever queries for status = OPEN, so an
        // ACKNOWLEDGED alert existing for this (user, rule) pair simply
        // never matches it - the lookup returns empty here exactly as it
        // would in the real repository/database.
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN))
            .thenReturn(Optional.empty());
        Alert inserted = new Alert();
        inserted.setId(UUID.randomUUID());
        when(alertInsertExecutor.insertAlert(any(Alert.class))).thenReturn(inserted);

        Optional<Alert> result = alertService.createAlertFrom(detectedResult(userId, null));

        assertTrue(result.isPresent());
        assertSame(inserted, result.get());
        verify(alertInsertExecutor).insertAlert(any(Alert.class));
    }

    // --- a RESOLVED existing alert does not suppress a new OPEN alert ---

    @Test
    void createAlertFrom_existingResolvedAlert_doesNotSuppressNewOpenAlert() {
        User user = new User();
        user.setId(userId);
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN))
            .thenReturn(Optional.empty());
        Alert inserted = new Alert();
        inserted.setId(UUID.randomUUID());
        when(alertInsertExecutor.insertAlert(any(Alert.class))).thenReturn(inserted);

        Optional<Alert> result = alertService.createAlertFrom(detectedResult(userId, null));

        assertTrue(result.isPresent());
        assertSame(inserted, result.get());
        verify(alertInsertExecutor).insertAlert(any(Alert.class));
    }

    // --- null userId skips deduplication entirely ---

    @Test
    void createAlertFrom_nullUserId_skipsDeduplication() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Alert> result = alertService.createAlertFrom(detectedResult(null, null));

        assertTrue(result.isPresent());
        verify(alertRepository, never())
            .findByUser_IdAndRule_IdAndStatus(any(UUID.class), any(UUID.class), any(Alert.Status.class));
        verifyNoInteractions(alertInsertExecutor);
        verify(alertRepository).save(any(Alert.class));
    }

    // --- different users for the same rule do not deduplicate against each other ---

    @Test
    void createAlertFrom_differentUsersSameRule_doNotDeduplicate() {
        UUID userIdA = UUID.randomUUID();
        UUID userIdB = UUID.randomUUID();
        User userA = new User();
        userA.setId(userIdA);
        User userB = new User();
        userB.setId(userIdB);

        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userIdA)).thenReturn(Optional.of(userA));
        when(userRepository.findById(userIdB)).thenReturn(Optional.of(userB));
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(eq(userIdA), eq(ruleId), eq(Alert.Status.OPEN)))
            .thenReturn(Optional.empty());
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(eq(userIdB), eq(ruleId), eq(Alert.Status.OPEN)))
            .thenReturn(Optional.empty());
        when(alertInsertExecutor.insertAlert(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(userIdA, null));
        alertService.createAlertFrom(detectedResult(userIdB, null));

        verify(alertRepository).findByUser_IdAndRule_IdAndStatus(userIdA, ruleId, Alert.Status.OPEN);
        verify(alertRepository).findByUser_IdAndRule_IdAndStatus(userIdB, ruleId, Alert.Status.OPEN);
        verify(alertInsertExecutor, times(2)).insertAlert(any(Alert.class));
    }

    // --- different rules for the same user do not deduplicate against each other ---

    @Test
    void createAlertFrom_differentRulesSameUser_doNotDeduplicate() {
        UUID ruleIdA = ruleId;
        UUID ruleIdB = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(detectionRuleRepository.findById(ruleIdA)).thenReturn(Optional.of(rule(ruleIdA)));
        when(detectionRuleRepository.findById(ruleIdB)).thenReturn(Optional.of(rule(ruleIdB)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(eq(userId), eq(ruleIdA), eq(Alert.Status.OPEN)))
            .thenReturn(Optional.empty());
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(eq(userId), eq(ruleIdB), eq(Alert.Status.OPEN)))
            .thenReturn(Optional.empty());
        when(alertInsertExecutor.insertAlert(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(ruleIdA, userId, null));
        alertService.createAlertFrom(detectedResult(ruleIdB, userId, null));

        verify(alertRepository).findByUser_IdAndRule_IdAndStatus(userId, ruleIdA, Alert.Status.OPEN);
        verify(alertRepository).findByUser_IdAndRule_IdAndStatus(userId, ruleIdB, Alert.Status.OPEN);
        verify(alertInsertExecutor, times(2)).insertAlert(any(Alert.class));
        assertNotEquals(ruleIdA, ruleIdB);
    }

    // =====================================================================
    // WebSocket alert publishing
    // =====================================================================

    @Test
    void createAlertFrom_newAlert_invokesAlertPublisher() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(null, null));

        verify(alertPublisher).publishAlert(any(AlertResponse.class));
    }

    @Test
    void createAlertFrom_dedupReuseExistingOpenAlert_doesNotInvokePublisher() {
        User user = new User();
        user.setId(userId);
        Alert existing = new Alert();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setStatus(Alert.Status.OPEN);
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(alertRepository.findByUser_IdAndRule_IdAndStatus(userId, ruleId, Alert.Status.OPEN))
            .thenReturn(Optional.of(existing));

        alertService.createAlertFrom(detectedResult(userId, null));

        verify(alertPublisher, never()).publishAlert(any());
    }

    @Test
    void acknowledgeAlert_invokesAlertPublisher() {
        UUID alertId = UUID.randomUUID();
        Alert alert = new Alert();
        alert.setId(alertId);
        alert.setStatus(Alert.Status.OPEN);
        User user = new User();
        user.setId(userId);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.acknowledgeAlert(alertId, userId);

        verify(alertPublisher).publishAlert(any(AlertResponse.class));
    }

    @Test
    void resolveAlert_invokesAlertPublisher() {
        UUID alertId = UUID.randomUUID();
        Alert alert = new Alert();
        alert.setId(alertId);
        alert.setStatus(Alert.Status.OPEN);
        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> inv.getArgument(0));

        alertService.resolveAlert(alertId);

        verify(alertPublisher).publishAlert(any(AlertResponse.class));
    }

}
