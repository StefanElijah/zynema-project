package dev.zynema.playback.controller;

import dev.zynema.playback.dto.PositionRequest;
import dev.zynema.playback.dto.SessionDto;
import dev.zynema.playback.dto.StartSessionRequest;
import dev.zynema.playback.service.PlaybackService;
import dev.zynema.playback.service.PlaybackQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/playback/sessions")
@RequiredArgsConstructor
@Tag(name = "Playback", description = "Playback sessions: start, heartbeat, end")
public class PlaybackController {

    private final PlaybackService playbackService;
    private final PlaybackQueryService queryService;

    @PostMapping
    @Operation(summary = "Start (or resume) a session",
               description = "Validates the title against catalog and enforces the plan's concurrent streams.")
    public SessionDto start(@Valid @RequestBody StartSessionRequest request,
                            @AuthenticationPrincipal Jwt jwt) {
        return playbackService.start(request, jwt);
    }

    @PutMapping("/{sessionId}/position")
    @Operation(summary = "Report the current position",
               description = "Also forwards the progress to user-service (best effort).")
    public SessionDto heartbeat(@PathVariable UUID sessionId,
                                @Valid @RequestBody PositionRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return playbackService.heartbeat(sessionId, request, jwt);
    }

    @PostMapping("/{sessionId}/end")
    @Operation(summary = "Close a session, recording the final position")
    public SessionDto end(@PathVariable UUID sessionId,
                          @Valid @RequestBody PositionRequest request,
                          @AuthenticationPrincipal Jwt jwt) {
        return playbackService.end(sessionId, request, jwt);
    }

    @GetMapping("/me/active")
    @Operation(summary = "Sessions I have open right now")
    public List<SessionDto> activeSessions(@AuthenticationPrincipal Jwt jwt) {
        return queryService.activeSessions(jwt);
    }
}
