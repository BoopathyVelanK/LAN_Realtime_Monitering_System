package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.entity.AuthFailureEvent;
import com.securesoc.entity.User;
import com.securesoc.repository.AuthFailureEventRepository;
import com.securesoc.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthFailureRecorderTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthFailureEventRepository authFailureEventRepository;

    private AuthFailureRecorder recorder;

    private UUID userId;

    @BeforeEach
    void setUp() {
        recorder = new AuthFailureRecorder(userRepository, authFailureEventRepository);
        userId = UUID.randomUUID();
    }

    private User user(int failedLoginAttempts) {
        User user = new User();
        user.setId(userId);
        user.setUsername("someone");
        user.setEmail("someone@example.invalid");
        user.setPasswordHash("hash");
        user.setFullName("Someone");
        user.setFailedLoginAttempts(failedLoginAttempts);
        user.setLockedUntil(null);
        return user;
    }

    // --- unknown user: no-op ---

    @Test
    void recordFailure_unknownUser_returnsNullAndSavesNothing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        DetectionContext result = recorder.recordFailure(userId, "127.0.0.1");

        assertNull(result);
        verify(userRepository, never()).save(any(User.class));
        verify(authFailureEventRepository, never()).save(any(AuthFailureEvent.class));
    }

    // --- counter increments, below lockout threshold ---

    @Test
    void recordFailure_belowThreshold_incrementsCounterWithoutLocking() {
        User user = user(2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        recorder.recordFailure(userId, "127.0.0.1");

        verify(userRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getFailedLoginAttempts());
        assertNull(captor.getValue().getLockedUntil(), "Should not lock before reaching the threshold");
    }

    // --- counter reaches threshold: account gets locked ---

    @Test
    void recordFailure_reachesThreshold_locksAccount() {
        User user = user(4); // about to become the 5th failure
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        recorder.recordFailure(userId, "127.0.0.1");

        verify(userRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getFailedLoginAttempts());
        assertNotNull(captor.getValue().getLockedUntil(), "Should lock once the threshold (5) is reached");
        assertTrue(captor.getValue().getLockedUntil().isAfter(Instant.now()));
    }

    // --- AuthFailureEvent persistence ---

    @Test
    void recordFailure_savesAuthFailureEventWithUserAndSourceIp() {
        User user = user(0);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        ArgumentCaptor<AuthFailureEvent> captor = ArgumentCaptor.forClass(AuthFailureEvent.class);
        when(authFailureEventRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        recorder.recordFailure(userId, "203.0.113.7");

        AuthFailureEvent saved = captor.getValue();
        assertSame(user, saved.getUser());
        assertEquals("203.0.113.7", saved.getSourceIp());
    }

    // --- returned DetectionContext shape ---

    @Test
    void recordFailure_returnsDetectionContextMatchingSavedEvent() {
        User user = user(0);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authFailureEventRepository.save(any(AuthFailureEvent.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        DetectionContext context = recorder.recordFailure(userId, "127.0.0.1");

        assertNotNull(context);
        assertEquals("AUTH_FAILURE", context.eventSource());
        assertNull(context.endpointId(), "A portal login has no EndpointDevice relationship to carry through");
        assertEquals(userId, context.userId());
        assertNotNull(context.occurredAt());
        assertTrue(context.event() instanceof AuthFailureEvent);
        assertEquals(userId, ((AuthFailureEvent) context.event()).getUser().getId());
    }
}
