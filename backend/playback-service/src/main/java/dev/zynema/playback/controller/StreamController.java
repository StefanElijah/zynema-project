package dev.zynema.playback.controller;

import dev.zynema.playback.service.StreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The HLS entry points of a session.
 *
 * <p>Manifests, not media: the master and the variant playlists are rewritten
 * on the way out (ADR-0024). Segments never pass through here — they are
 * presigned to the storage edge, which is what keeps a service from streaming
 * video through a Java thread.
 *
 * <p>Both endpoints require the caller's token and check session ownership:
 * knowing a session id is not enough to watch somebody else's stream.
 */
@RestController
@RequestMapping("/api/v1/playback/stream")
@RequiredArgsConstructor
@Tag(name = "Playback — Stream", description = "HLS manifests for a playback session")
public class StreamController {

    private static final String HLS = "application/vnd.apple.mpegurl";

    private final StreamService streamService;

    @GetMapping(value = "/{sessionId}/master.m3u8", produces = HLS)
    @Operation(summary = "Master playlist, pointing back at this service")
    public String master(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        return streamService.master(sessionId, jwt);
    }

    @GetMapping(value = "/{sessionId}/{variant}/playlist.m3u8", produces = HLS)
    @Operation(summary = "Variant playlist with presigned segment URLs")
    public String variant(@PathVariable UUID sessionId,
                          @PathVariable String variant,
                          @AuthenticationPrincipal Jwt jwt) {
        return streamService.variant(sessionId, variant, jwt);
    }
}
