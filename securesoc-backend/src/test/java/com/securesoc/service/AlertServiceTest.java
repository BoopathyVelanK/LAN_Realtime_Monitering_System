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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    private AlertService alertService;

    private UUID ruleId;
    private UUID userId;
    private UUID endpointId;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(
            alertRepository, detectionRuleRepository, userRepository, endpointDeviceRepository);
        ruleId = UUID.randomUUID();
        userId = UUID.randomUUID();
        endpointId = UUID.randomUUID();
    }

    private DetectionRule rule() {
        DetectionRule rule = new DetectionRule();
        rule.setId(ruleId);
        rule.setName("Repeated failed login");
        rule.setRuleType(DetectionRule.RuleType.THRESHOLD);
        rule.setEventSource("AUTH_FAILURE");
        rule.setSeverity(DetectionRule.Severity.HIGH);
        return rule;
    }

    private DetectionResult detectedResult(UUID userId, UUID endpointId) {
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

    // --- detected result creates and saves an Alert ---

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
        verifyNoInteractions(alertRepository, detectionRuleRepository, userRepository, endpointDeviceRepository);
    }

    @Test
    void createAlertFrom_notDetected_doesNotSaveAlert() {
        DetectionResult notDetected = new DetectionResult(
            false, ruleId, DetectionRule.Severity.HIGH, "t", "d", userId, endpointId);

        Optional<Alert> result = alertService.createAlertFrom(notDetected);

        assertTrue(result.isEmpty());
        verifyNoInteractions(alertRepository, detectionRuleRepository, userRepository, endpointDeviceRepository);
    }

    @Test
    void createAlertFrom_null_doesNotSaveAlert() {
        Optional<Alert> result = alertService.createAlertFrom(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(alertRepository, detectionRuleRepository, userRepository, endpointDeviceRepository);
    }

    // --- userId is mapped when present ---

    @Test
    void createAlertFrom_userIdPresent_mapsUser() {
        User user = new User();
        user.setId(userId);
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        when(alertRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

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

    // --- endpointId is mapped when present ---

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
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        when(alertRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        alertService.createAlertFrom(detectedResult(userId, endpointId));

        assertEquals(user, captor.getValue().getUser());
        assertEquals(endpoint, captor.getValue().getEndpoint());
        verify(userRepository).findById(userId);
        verify(endpointDeviceRepository).findById(endpointId);
        verify(alertRepository).save(any(Alert.class));
    }

    // --- missing required DetectionRule is handled correctly ---

    @Test
    void createAlertFrom_missingRule_throwsResourceNotFoundExceptionAndDoesNotSave() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> alertService.createAlertFrom(detectedResult(null, null)));

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void createAlertFrom_missingUser_throwsResourceNotFoundExceptionAndDoesNotSave() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> alertService.createAlertFrom(detectedResult(userId, null)));

        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void createAlertFrom_missingEndpoint_throwsResourceNotFoundExceptionAndDoesNotSave() {
        when(detectionRuleRepository.findById(ruleId)).thenReturn(Optional.of(rule()));
        when(endpointDeviceRepository.findById(endpointId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> alertService.createAlertFrom(detectedResult(null, endpointId)));

        verify(alertRepository, never()).save(any(Alert.class));
    }
}
