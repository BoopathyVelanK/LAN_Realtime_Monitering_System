package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Schema-only for now - see V5__phase4_detection_foundation.sql. No
 * RiskEngine writes to this yet; that's a later phase. One live row per
 * endpoint (unique index on endpoint_id) - historical scoring is a
 * separate future table, not this one. */
@Entity
@Table(name = "risk_scores")
@Getter
@Setter
@NoArgsConstructor
public class RiskScore {

    public enum Level { SAFE, LOW, MEDIUM, HIGH, CRITICAL }

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private EndpointDevice endpoint;

    @Column(nullable = false)
    private short score = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Level level = Level.SAFE;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
