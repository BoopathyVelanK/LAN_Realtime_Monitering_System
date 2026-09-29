package com.securesoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** A single process within a {@link RunningAppSnapshot}. windowTitle is
 * always null until collector.py's pywin32 hook is wired up on real
 * Windows hardware - see collector.py's get_running_applications
 * docstring. commandLine is nullable: it is null for snapshots ingested
 * from an older agent build that did not yet send it (see
 * RunningAppsRequest.AppEntry), and PowerShellDetector's repository query
 * simply never matches a null commandLine rather than erroring. */
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

    /** Raw process command line, as reported by the agent's psutil-based
     * collector. TEXT rather than a bounded varchar because a real-world
     * command line (e.g. a PowerShell -EncodedCommand base64 payload) can
     * run to several thousand characters, and silently truncating it
     * would risk breaking PowerShellDetector's substring match against
     * the tail of the string. Never logged at INFO level - see
     * PowerShellDetector's Javadoc. */
    @Column(name = "command_line", columnDefinition = "TEXT")
    private String commandLine;
}
