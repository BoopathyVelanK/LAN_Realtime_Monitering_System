package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One row per monitoring-cycle process snapshot from
 * collector.get_running_applications(); the individual processes live in
 * {@link RunningApp} rather than a JSON blob so Phase 4 detection rules
 * can filter/aggregate by process name directly in SQL. */
@Entity
@Table(name = "running_app_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class RunningAppSnapshot {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private EndpointDevice endpoint;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt = Instant.now();

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RunningApp> apps = new ArrayList<>();
}
