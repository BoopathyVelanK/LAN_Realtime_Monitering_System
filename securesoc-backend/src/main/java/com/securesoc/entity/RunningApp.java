package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** A single process within a {@link RunningAppSnapshot}. windowTitle is
 * always null until collector.py's pywin32 hook is wired up on real
 * Windows hardware - see collector.py's get_running_applications
 * docstring. */
@Entity
@Table(name = "running_apps")
@Getter
@Setter
@NoArgsConstructor
public class RunningApp {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private RunningAppSnapshot snapshot;

    @Column(name = "process_name", length = 255)
    private String processName;

    @Column(name = "window_title", length = 500)
    private String windowTitle;

    @Column(name = "pid")
    private Integer pid;
}
