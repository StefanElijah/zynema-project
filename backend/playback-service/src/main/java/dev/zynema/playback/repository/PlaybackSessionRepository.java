package dev.zynema.playback.repository;

import dev.zynema.playback.domain.PlaybackSession;
import dev.zynema.playback.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaybackSessionRepository extends JpaRepository<PlaybackSession, UUID> {

    Optional<PlaybackSession> findByIdAndUserId(UUID id, UUID userId);

    List<PlaybackSession> findByUserIdAndStatusNotOrderByLastHeartbeatAtDesc(UUID userId, SessionStatus status);

    long countByUserIdAndStatusNot(UUID userId, SessionStatus status);

    /**
     * The open session of an episode. Two derived queries instead of one with a
     * null parameter: SQL cannot compare "= NULL", and a null-safe JPQL
     * expression depending on parameter typing is a portability trap.
     */
    Optional<PlaybackSession> findByProfileIdAndContentIdAndEpisodeIdAndStatusNot(
        UUID profileId, UUID contentId, UUID episodeId, SessionStatus status);

    Optional<PlaybackSession> findByProfileIdAndContentIdAndEpisodeIdIsNullAndStatusNot(
        UUID profileId, UUID contentId, SessionStatus status);
}
