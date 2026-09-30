package dev.zynema.bff.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The detail screen plus the two pieces of <em>user context</em> that make it
 * useful once somebody is signed in: whether they may watch, and where they
 * left off.
 *
 * <p>Both are optional. When payment or user-service is unavailable the content
 * still renders — an unavailable section is reported in {@code degraded}, never
 * silently dropped — because losing the "resume" hint must not cost the
 * visitor the page.
 */
public record ContentView(
    Detail content,
    PlaybackAvailability playback,
    Progress progress,
    List<WebSection> degraded
) {

    public record Detail(
        UUID id,
        ContentKind type,
        String slug,
        String title,
        String originalTitle,
        String synopsis,
        String tagline,
        Integer releaseYear,
        String maturityRating,
        Integer runtimeMinutes,
        String posterUrl,
        String backdropUrl,
        String trailerUrl,
        BigDecimal averageRating,
        Integer popularity,
        List<GenreRef> genres,
        List<Season> seasons,
        List<Credit> credits
    ) {
    }

    public record Season(Integer seasonNumber, String title, Integer releaseYear, Integer episodeCount) {
    }

    public record Credit(String personName, String role, String characterName) {
    }

    /**
     * @param allowed  whether the caller could start playback right now
     * @param reason   why not, when it is false; {@code null} when allowed
     */
    public record PlaybackAvailability(boolean allowed, PlaybackReason reason) {
    }

    public enum PlaybackReason {
        /** No token: browsing is public, watching is not. */
        AUTHENTICATION_REQUIRED,
        /** Signed in, but the account is not paying for a plan right now. */
        SUBSCRIPTION_REQUIRED,
        /**
         * The plan could not be checked. Playback refuses to guess: the answer
         * is "try again", never an accidental "yes" or a wrong paywall.
         */
        UNAVAILABLE
    }

    /**
     * Where the profile left this title. {@code null} when nothing was watched
     * yet, or nobody asked (no profile in the request).
     */
    public record Progress(
        int positionSeconds,
        Integer durationSeconds,
        boolean completed,
        Instant lastWatchedAt,
        UUID episodeId
    ) {
    }

    /**
     * The happy path: nothing missing, nothing degraded.
     */
    public static ContentView of(Detail content, PlaybackAvailability playback, Progress progress) {
        return new ContentView(content, playback, progress, List.of());
    }
}
