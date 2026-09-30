package dev.zynema.bff.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The profile-scoped rails: "continue watching" and "my list".
 *
 * <p>user-service owns the activity (it stores ids and positions); catalog owns
 * the artwork and titles. This view is the join — the reason a BFF exists.
 * Each rail degrades on its own, so a failing watchlist does not hide the
 * half-watched episode.
 */
public record ProfileHomeView(
    UUID profileId,
    List<ContinueWatching> continueWatching,
    List<Watchlist> myList,
    List<WebSection> degraded
) {

    public record ContinueWatching(
        TitleCard content,
        int positionSeconds,
        Integer durationSeconds,
        boolean completed,
        Instant lastWatchedAt,
        UUID episodeId
    ) {
    }

    public record Watchlist(TitleCard content, Instant addedAt) {
    }
}
