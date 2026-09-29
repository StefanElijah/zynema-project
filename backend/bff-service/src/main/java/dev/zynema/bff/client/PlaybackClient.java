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
 * playback-service over HTTP: the session lifecycle the player drives.
 *
 * <p>The BFF does not compose anything here — it is the SPA's single entry
 * point (ADR-0004), so these are deliberate pass-throughs with the token
 * relayed: playback decides entitlement, concurrency and stream paths, and the
 * session payload (including {@code streamPath}) is the player's contract.
 *
 * <p>The nested records mirror the service's DTOs on purpose: the players of
 * both sides of the wire are the same object by contract, and the generated TS
 * client publishes this shape to the SPA.
 */
@Component
public class PlaybackClient {

    private final WebClient webClient;

    public PlaybackClient(@Qualifier("playbackWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<Session> startSession(UUID profileId, UUID contentId, UUID episodeId, String device) {
        return webClient.post()
            .uri("/api/v1/playback/sessions")
            .bodyValue(new StartSessionBody(profileId, contentId, episodeId, device))
            .retrieve()
            .bodyToMono(Session.class);
    }

    public Mono<Session> heartbeat(UUID sessionId, int positionSeconds) {
        return webClient.put()
            .uri("/api/v1/playback/sessions/%s/position".formatted(sessionId))
            .bodyValue(new PositionBody(positionSeconds))
            .retrieve()
            .bodyToMono(Session.class);
    }

    public Mono<Session> endSession(UUID sessionId, int positionSeconds) {
        return webClient.post()
            .uri("/api/v1/playback/sessions/%s/end".formatted(sessionId))
            .bodyValue(new PositionBody(positionSeconds))
            .retrieve()
            .bodyToMono(Session.class);
    }

    public Mono<List<Session>> activeSessions() {
        return webClient.get()
            .uri("/api/v1/playback/sessions/me/active")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Session>>() {
            });
    }

    record StartSessionBody(UUID profileId, UUID contentId, UUID episodeId, String device) {
    }

    record PositionBody(Integer positionSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(
        UUID id,
        UUID profileId,
        UUID contentId,
        UUID episodeId,
        String contentTitle,
        String status,
        Integer positionSeconds,
        Integer durationSeconds,
        String device,
        String streamPath,
        Instant startedAt
    ) {
    }
}
