package dev.zynema.bff.controller;

import dev.zynema.bff.client.DownstreamErrors;
import dev.zynema.bff.client.PlaybackClient;
import dev.zynema.bff.service.UpstreamGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * The session lifecycle, pass-through.
 *
 * <p>These endpoints decide nothing: playback-service enforces entitlement
 * (this is where {@code 402} and {@code 409 CONTENT_NOT_READY} come from),
 * concurrency and stream paths. The BFF exists here as the SPA's single entry
 * point (ADR-0004) — the player never talks to playback directly — and to keep
 * the resilience policy in one place.
 *
 * <p>The session payload, {@code streamPath} included, is returned as playback
 * defines it: it is the player's contract, not a view to reshape.
 */
@RestController
@RequestMapping("/api/v1/web/playback/sessions")
@RequiredArgsConstructor
@Tag(name = "Web BFF", description = "Aggregated views for the SPA")
public class PlaybackWebController {

    private final UpstreamGateway gateway;

    @PostMapping
    @Operation(summary = "Start (or resume) a playback session",
        description = "402 when the account has no active plan, 422 when the plan's stream limit is reached")
    public Mono<PlaybackClient.Session> start(@Valid @RequestBody StartRequest request) {
        return gateway.startSession(request.profileId(), request.contentId(), request.episodeId(), request.device())
            .transform(publisher -> DownstreamErrors.toApiError("playback-service", publisher));
    }

    @PutMapping("/{sessionId}/position")
    @Operation(summary = "Heartbeat: record the position the player is at")
    public Mono<PlaybackClient.Session> heartbeat(@PathVariable UUID sessionId,
                                                  @Valid @RequestBody PositionRequest request) {
        return gateway.heartbeat(sessionId, request.positionSeconds())
            .transform(publisher -> DownstreamErrors.toApiError("playback-service", publisher));
    }

    @PostMapping("/{sessionId}/end")
    @Operation(summary = "Close a session with the final position")
    public Mono<PlaybackClient.Session> end(@PathVariable UUID sessionId,
                                            @Valid @RequestBody PositionRequest request) {
        return gateway.endSession(sessionId, request.positionSeconds())
            .transform(publisher -> DownstreamErrors.toApiError("playback-service", publisher));
    }

    @GetMapping("/me/active")
    @Operation(summary = "Open sessions of the account, newest first")
    public Mono<List<PlaybackClient.Session>> active() {
        return gateway.activeSessions()
            .transform(publisher -> DownstreamErrors.toApiError("playback-service", publisher));
    }

    public record StartRequest(
        @NotNull UUID profileId,
        @NotNull UUID contentId,
        UUID episodeId,
        @Size(max = 60) String device
    ) {
    }

    public record PositionRequest(@NotNull @Min(0) Integer positionSeconds) {
    }
}
