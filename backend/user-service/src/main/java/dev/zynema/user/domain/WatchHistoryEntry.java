package dev.zynema.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Playback progress. One row per (profile, movie) or (profile, episode),
 * enforced by two partial unique indexes in the database. Progress updates
 * are idempotent upserts, so a client retry never duplicates a row.
 */
@Entity
@Table(name = "watch_history")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WatchHistoryEntry {

    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "episode_id")
    private UUID episodeId;

    @Column(name = "position_seconds", nullable = false)
    private Integer positionSeconds = 0;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "last_watched_at", nullable = false)
    private Instant lastWatchedAt = Instant.now();
}
