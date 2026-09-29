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

    public enum RuleType { THRESHOLD, PROCESS_MATCH, POWERSHELL_MATCH }

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

    /** Exact, case-insensitive process name a PROCESS_MATCH rule watches
     * for (e.g. "powershell.exe") - see SuspiciousProcessDetector. Null
     * for every other rule type, same as threshold/windowSeconds being
     * null for non-THRESHOLD rules. */
    @Column(name = "process_name", length = 255)
    private String processName;

    /** Case-insensitive substring to match against a RunningApp's
     * commandLine, for a POWERSHELL_MATCH rule (e.g. "-encodedcommand",
     * "downloadstring") - used together with processName (reused from
     * PROCESS_MATCH above) - see PowerShellDetector. Null for every other
     * rule type, same as processName being null for non-PROCESS_MATCH/
     * non-POWERSHELL_MATCH rules. */
    @Column(name = "command_pattern", length = 500)
    private String commandPattern;

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
