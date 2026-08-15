package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Schema-only for now - see V5__phase4_detection_foundation.sql. No
 * DetectionEngine reads/writes this yet; that's the next phase. */
@Entity
@Table(name = "detection_rules")
@Getter
@Setter
@NoArgsConstructor
public class DetectionRule {

    public enum RuleType { THRESHOLD }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private RuleType ruleType = RuleType.THRESHOLD;

    /** e.g. "AUTH_FAILURE", "USB_EVENT" - what event stream this rule
     * evaluates against. Not an enum yet since the full set of sources
     * this will eventually cover isn't settled. */
    @Column(name = "event_source", nullable = false, length = 50)
    private String eventSource;

    @Column
    private Integer threshold;

    @Column(name = "window_seconds")
    private Integer windowSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
