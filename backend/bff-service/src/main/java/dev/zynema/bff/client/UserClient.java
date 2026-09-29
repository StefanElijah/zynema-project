package dev.zynema.bff.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * user-service reads: the local account and one profile's activity.
 *
 * <p>Everything here is self-service ({@code /users/me/...}) and the token is
 * relayed, so ownership is enforced by the service that owns the data: the BFF
 * cannot be tricked into reading somebody else's watchlist.
 */
@Component
public class UserClient {

    private final WebClient webClient;

    public UserClient(@Qualifier("userWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<UserAccount> me() {
        return webClient.get()
            .uri("/api/v1/users/me")
            .retrieve()
            .bodyToMono(UserAccount.class);
    }

    public Mono<List<WatchHistory>> continueWatching(UUID profileId) {
        // Explicit parameterised types, not a generic helper: a method type
        // variable is erased and Jackson would happily build a List<LinkedHashMap>.
        return webClient.get()
            .uri("/api/v1/users/me/profiles/%s/continue-watching".formatted(profileId))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<WatchHistory>>() {
            });
    }

    public Mono<List<WatchlistEntry>> watchlist(UUID profileId) {
        return webClient.get()
            .uri("/api/v1/users/me/profiles/%s/watchlist".formatted(profileId))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<WatchlistEntry>>() {
            });
    }

    // ───────────────────── profile management (Fase 8) ──────────────────────

    public Mono<List<Profile>> profiles() {
        return webClient.get()
            .uri("/api/v1/users/me/profiles")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Profile>>() {
            });
    }

    public Mono<Profile> createProfile(String name, String avatarKey, Boolean kids, String language) {
        return webClient.post()
            .uri("/api/v1/users/me/profiles")
            .bodyValue(new ProfileCreateBody(name, avatarKey, kids, language))
            .retrieve()
            .bodyToMono(Profile.class);
    }

    public Mono<Void> deleteProfile(UUID profileId) {
        return webClient.delete()
            .uri("/api/v1/users/me/profiles/%s".formatted(profileId))
            .retrieve()
            .bodyToMono(Void.class);
    }

    public Mono<WatchlistEntry> addToWatchlist(UUID profileId, UUID contentId) {
        return webClient.post()
            .uri("/api/v1/users/me/profiles/%s/watchlist".formatted(profileId))
            .bodyValue(new WatchlistBody(contentId))
            .retrieve()
            .bodyToMono(WatchlistEntry.class);
    }

    public Mono<Void> removeFromWatchlist(UUID profileId, UUID contentId) {
        return webClient.delete()
            .uri("/api/v1/users/me/profiles/%s/watchlist/%s".formatted(profileId, contentId))
            .retrieve()
            .bodyToMono(Void.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileCreateBody(String name, String avatarKey, Boolean kids, String language) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WatchlistBody(UUID contentId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserAccount(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String preferredLanguage,
        List<Profile> profiles
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(UUID id, String name, String avatarKey, boolean kids, String language) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WatchHistory(
        UUID id,
        UUID contentId,
        UUID episodeId,
        Integer positionSeconds,
        Integer durationSeconds,
        boolean completed,
        Instant lastWatchedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WatchlistEntry(UUID id, UUID contentId, Instant addedAt) {
    }
}
