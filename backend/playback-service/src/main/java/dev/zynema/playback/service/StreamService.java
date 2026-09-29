package dev.zynema.playback.service;

import dev.zynema.common.exception.ConflictException;
import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.playback.client.CatalogServiceClient;
import dev.zynema.playback.domain.PlaybackSession;
import dev.zynema.playback.repository.PlaybackSessionRepository;
import dev.zynema.playback.storage.HlsStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Serves HLS manifests for a session, rewritten for signed delivery.
 *
 * <p>HLS and presigned URLs do not compose by default: a playlist references
 * its children relatively, and a relative reference drops the query string —
 * signature included. So the manifests are <strong>rewritten here</strong>:
 * <ul>
 *   <li>the master playlist points each variant back at this service (the
 *       player keeps using its token for manifests),</li>
 *   <li>a variant playlist embeds a short-lived presigned URL per segment
 *       (ADR-0024), which is what lets the browser pull bytes from the edge
 *       without credentials.</li>
 * </ul>
 *
 * <p>Everything is authorized the same way the rest of playback is: the caller
 * must be the session's owner, and the catalogue — not the storage bucket —
 * decides whether a rendition exists at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService {

    private static final String MASTER = "/master.m3u8";
    private static final Pattern VARIANT_NAME = Pattern.compile("[A-Za-z0-9]{2,12}");

    private final PlaybackSessionRepository sessionRepository;
    private final PlaybackDependencies dependencies;
    private final CatalogServiceClient catalog;
    private final HlsStorage storage;

    @Transactional(readOnly = true)
    public String master(UUID sessionId, Jwt jwt) {
        PlaybackSession session = ownedSession(sessionId, jwt);
        String prefix = renditionPrefix(session);

        String manifest = storage.read(prefix + MASTER)
            .orElseThrow(() -> new ResourceNotFoundException("Rendition", sessionId));

        String base = "/api/v1/playback/stream/" + sessionId;
        return rewrite(manifest, uri -> {
            if (!uri.endsWith(".m3u8")) {
                return uri; // EXT-X-MEDIA lines and the like: leave untouched
            }
            String variant = uri.substring(0, uri.indexOf('/'));
            return VARIANT_NAME.matcher(variant).matches() ? base + "/" + variant + "/playlist.m3u8" : uri;
        });
    }

    @Transactional(readOnly = true)
    public String variant(UUID sessionId, String variant, Jwt jwt) {
        if (!VARIANT_NAME.matcher(variant).matches()) {
            throw new ResourceNotFoundException("Rendition", variant);
        }
        PlaybackSession session = ownedSession(sessionId, jwt);
        String prefix = renditionPrefix(session);
        String variantDirectory = prefix + "/" + variant + "/";

        String manifest = storage.read(variantDirectory + "playlist.m3u8")
            .orElseThrow(() -> new ResourceNotFoundException("Rendition", variant));

        return rewrite(manifest, uri -> uri.endsWith(".ts") || uri.endsWith(".m4s")
            ? storage.presignedUrl(variantDirectory + uri)
            : uri);
    }

    // ───────────────────────────── helpers ────────────────────────────

    private PlaybackSession ownedSession(UUID sessionId, Jwt jwt) {
        UUID userId = dependencies.resolveUserId(jwt);
        return sessionRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Playback session", sessionId));
    }

    /**
     * The catalogue's {@code hls_path} holds the master playlist of the title or
     * the episode; the renditions live next to it.
     */
    private String renditionPrefix(PlaybackSession session) {
        String hlsPath = session.getEpisodeId() != null
            ? catalog.episode(session.getEpisodeId()).hlsPath()
            : catalog.currentContent(session.getContentId()).hlsPath();

        if (hlsPath == null || hlsPath.isBlank()) {
            // The content exists and the account may watch it; there is simply
            // nothing rendered yet. That is a state, not an error.
            throw new ConflictException(
                "This title has no rendition yet: the video pipeline has not published it");
        }
        int lastSlash = hlsPath.lastIndexOf('/');
        return lastSlash > 0 ? hlsPath.substring(0, lastSlash) : hlsPath;
    }

    /**
     * Only URI lines are touched: tags ({@code #EXT...}) carry the playlist's
     * meaning and are forwarded verbatim.
     */
    private String rewrite(String manifest, java.util.function.UnaryOperator<String> rule) {
        List<String> lines = manifest.lines().map(line ->
            line.isBlank() || line.startsWith("#") ? line : rule.apply(line.trim())).toList();
        return String.join("\n", lines) + "\n";
    }
}
