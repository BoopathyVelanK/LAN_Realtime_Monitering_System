package com.securesoc.service;

import com.securesoc.detection.DetectionContext;
import com.securesoc.detection.DetectionEngine;
import com.securesoc.dto.AuthResponse;
import com.securesoc.entity.User;
import com.securesoc.exception.AccountLockedException;
import com.securesoc.exception.UnauthorizedException;
import com.securesoc.repository.RefreshTokenRepository;
import com.securesoc.repository.UserRepository;
import com.securesoc.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused on Checkpoint E's wiring in AuthService.login(): that a
 * wrong-password attempt delegates to {@link AuthFailureRecorder} and then
 * {@link DetectionEngine}, that a successful login never touches either,
 * that the locked/disabled short-circuits never reach either, and that a
 * DetectionEngine failure can never replace the UnauthorizedException the
 * caller expects. AuthFailureRecorder's own persistence logic is covered
 * separately by {@link AuthFailureRecorderTest}; this class mocks it as a
 * collaborator rather than re-testing it.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuthFailureRecorder authFailureRecorder;
    @Mock
    private DetectionEngine detectionEngine;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository, refreshTokenRepository, authFailureRecorder, detectionEngine,
            passwordEncoder, jwtService);
        userId = UUID.randomUUID();
    }

    private User user() {
        User user = new User();
        user.setId(userId);
        user.setUsername("someone");
        user.setEmail("someone@example.invalid");
        user.setPasswordHash("hash");
        user.setFullName("Someone");
        user.setEnabled(true);
        user.setLockedUntil(null);
        return user;
    }

    // =====================================================================
    // Wrong password: delegates to AuthFailureRecorder, then DetectionEngine
    // =====================================================================

    @Test
    void login_wrongPassword_callsRecorderThenDetectionEngine_andThrowsUnauthorized() {
        User user = user();
        DetectionContext context = new DetectionContext("AUTH_FAILURE", null, userId, Instant.now(), null);
        when(userRepository.findByUsernameOrEmail("someone")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(authFailureRecorder.recordFailure(userId, "127.0.0.1")).thenReturn(context);

        assertThrows(UnauthorizedException.class,
            () -> authService.login("someone", "wrong", "127.0.0.1"));

        verify(authFailureRecorder).recordFailure(userId, "127.0.0.1");
        verify(detectionEngine).evaluate(context);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(refreshTokenRepository, jwtService);
    }

    @Test
    void login_wrongPassword_recorderReturnsNull_detectionEngineNeverCalled() {
        User user = user();
        when(userRepository.findByUsernameOrEmail("someone")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(authFailureRecorder.recordFailure(userId, "127.0.0.1")).thenReturn(null);

        assertThrows(UnauthorizedException.class,
            () -> authService.login("someone", "wrong", "127.0.0.1"));

        verify(authFailureRecorder).recordFailure(userId, "127.0.0.1");
        verifyNoInteractions(detectionEngine);
    }

    @Test
    void login_wrongPassword_detectionEngineThrows_stillThrowsUnauthorizedNotTheDetectionException() {
        User user = user();
        DetectionContext context = new DetectionContext("AUTH_FAILURE", null, userId, Instant.now(), null);
        when(userRepository.findByUsernameOrEmail("someone")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);
        when(authFailureRecorder.recordFailure(userId, "127.0.0.1")).thenReturn(context);
        when(detectionEngine.evaluate(context)).thenThrow(new RuntimeException("boom"));

        // Must surface as the normal "invalid credentials" response, not
        // whatever exception the detection engine happened to throw - a
        // detection-layer failure must never change the caller-visible
        // outcome of a login attempt.
        UnauthorizedException thrown = assertThrows(UnauthorizedException.class,
            () -> authService.login("someone", "wrong", "127.0.0.1"));
        assertEquals("Invalid username/email or password", thrown.getMessage());

        verify(authFailureRecorder).recordFailure(userId, "127.0.0.1");
        verify(detectionEngine).evaluate(context);
    }

    // =====================================================================
    // Successful login: neither AuthFailureRecorder nor DetectionEngine involved
    // =====================================================================

    @Test
    void login_correctPassword_neverCallsRecorderOrDetectionEngine() {
        User user = user();
        when(userRepository.findByUsernameOrEmail("someone")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        when(jwtService.generateAccessToken(eq(userId), eq("someone"), any()))
            .thenReturn("access-token");
        when(jwtService.getRefreshTokenTtlSeconds()).thenReturn(3600L);
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        AuthResponse response = authService.login("someone", "correct", "127.0.0.1");

        assertEquals("access-token", response.accessToken());
        verifyNoInteractions(authFailureRecorder, detectionEngine);
        verify(userRepository).save(user);
        assertEquals(0, user.getFailedLoginAttempts());
    }

    // =====================================================================
    // Locked / disabled / unknown-user short-circuits: unaffected by Checkpoint E
    // =====================================================================

    @Test
    void login_accountLocked_throwsAccountLockedException_neverInvolvesRecorderOrDetection() {
        User user = user();
        user.setLockedUntil(Instant.now().plusSeconds(60));
        when(userRepository.findByUsernameOrEmail("someone")).thenReturn(Optional.of(user));

        assertThrows(AccountLockedException.class,
            () -> authService.login("someone", "whatever", "127.0.0.1"));

        verifyNoInteractions(authFailureRecorder, detectionEngine, passwordEncoder);
    }

    @Test
    void login_accountDisabled_throwsUnauthorized_neverInvolvesRecorderOrDetection() {
        User user = user();
        user.setEnabled(false);
        when(userRepository.findByUsernameOrEmail("someone")).thenReturn(Optional.of(user));

        UnauthorizedException thrown = assertThrows(UnauthorizedException.class,
            () -> authService.login("someone", "whatever", "127.0.0.1"));
        assertEquals("This account has been disabled", thrown.getMessage());

        verifyNoInteractions(authFailureRecorder, detectionEngine, passwordEncoder);
    }

    @Test
    void login_unknownUser_throwsUnauthorized_neverInvolvesRecorderOrDetection() {
        when(userRepository.findByUsernameOrEmail("ghost")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
            () -> authService.login("ghost", "whatever", "127.0.0.1"));

        verifyNoInteractions(authFailureRecorder, detectionEngine, passwordEncoder);
    }
}
