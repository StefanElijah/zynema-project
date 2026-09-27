package dev.zynema.playback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An open or finished playback session.
 *
 * <p>It tracks the session itself — who is watching, on which profile and
 * device, where they are and whether it is still open. The durable watch
 * history is owned by user-service and updated on every heartbeat, so a crash
 * here loses at most the last few seconds of progress.
 */
@Entity
@Table(name = "playback_sessions")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PlaybackSession {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "episode_id")
    private UUID episodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.STARTED;

    /** Denormalised title, so the session list reads without calling catalog. */
    @Column(name = "content_title", length = 255)
    private String contentTitle;

    @Column(name = "position_seconds", nullable = false)
    private Integer positionSeconds = 0;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(length = 60)
    private String device;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    public boolean isOpen() {
        return status != SessionStatus.ENDED;
    }

    public void heartbeat(int positionSeconds, Instant when) {
        this.positionSeconds = Math.max(0, positionSeconds);
        this.lastHeartbeatAt = when;
    }

    public void end(int positionSeconds, Instant when) {
        heartbeat(positionSeconds, when);
        this.status = SessionStatus.ENDED;
        this.endedAt = when;
    }
}
