package com.securesoc.repository;

import com.securesoc.entity.AuthFailureEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuthFailureEventRepository extends JpaRepository<AuthFailureEvent, UUID> {

    /** For the future FailedLoginDetector: failures for a user since a
     * given instant, most recent first. Not called from anywhere yet. */
    List<AuthFailureEvent> findByUser_IdAndAttemptedAtAfterOrderByAttemptedAtDesc(UUID userId, Instant since);

    long countByUser_IdAndAttemptedAtAfter(UUID userId, Instant since);
}
