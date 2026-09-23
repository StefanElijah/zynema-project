package dev.zynema.user.repository;

import dev.zynema.user.domain.WatchHistoryEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchHistoryRepository extends JpaRepository<WatchHistoryEntry, UUID> {

    List<WatchHistoryEntry> findByProfileIdOrderByLastWatchedAtDesc(UUID profileId, Pageable pageable);

    Optional<WatchHistoryEntry> findByProfileIdAndContentIdAndEpisodeId(UUID profileId, UUID contentId, UUID episodeId);

    Optional<WatchHistoryEntry> findByProfileIdAndContentIdAndEpisodeIdIsNull(UUID profileId, UUID contentId);

    /**
     * In-progress entries, most recent first. Unlike a derived method this
     * does not try to match {@code episode_id = null}.
     */
    @Query("""
        SELECT h FROM WatchHistoryEntry h
        WHERE h.profile.id = :profileId
          AND h.completed = false
          AND h.positionSeconds > 0
        ORDER BY h.lastWatchedAt DESC
        """)
    List<WatchHistoryEntry> findInProgress(@Param("profileId") UUID profileId, Pageable pageable);
}
