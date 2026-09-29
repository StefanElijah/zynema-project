package dev.zynema.playback;

import com.github.tomakehurst.wiremock.client.WireMock;
import dev.zynema.playback.domain.PlaybackSession;
import dev.zynema.playback.domain.SessionStatus;
import dev.zynema.playback.repository.PlaybackSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HLS delivery (ADR-0024): manifests are rewritten for signed delivery, and
 * everything stays authorized by session ownership.
 */
@AutoConfigureMockMvc
class StreamApiTests extends AbstractPlaybackIntegrationTest {

    private static final String DEMO_SUBJECT = "11111111-1111-4111-8111-111111111111";
    private static final String DEMO_USER = "71000000-0000-4000-8000-000000000001";
    private static final String DEMO_PROFILE = "72000000-0000-4000-8000-000000000001";
    private static final UUID CONTENT_ID = UUID.fromString("a2000000-0000-4000-8000-000000000002");
    private static final UUID EPISODE_ID = UUID.fromString("e1000000-0000-4000-8000-000000000001");

    private static final String MASTER = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-STREAM-INF:BANDWIDTH=464000,RESOLUTION=426x240,CODECS="avc1.4d401e,mp4a.40.2"
        240p/playlist.m3u8
        """;

    private static final String VARIANT = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:6
        #EXTINF:6.0,
        segment_00000.ts
        #EXT-X-ENDLIST
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlaybackSessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        DEPENDENCIES.resetAll();
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/users/me"))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "email": "demo@zynema.dev", "displayName": "Demo User"}
                    """.formatted(DEMO_USER))));
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/catalog/contents/" + CONTENT_ID))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "title": "Arcane", "type": "SERIES", "runtimeMinutes": 40,
                     "hlsPath": "hls/contents/%s/master.m3u8"}
                    """.formatted(CONTENT_ID, CONTENT_ID))));
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/catalog/episodes/" + EPISODE_ID))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "hlsPath": "hls/episodes/%s/master.m3u8"}
                    """.formatted(EPISODE_ID, EPISODE_ID))));

        seedRendition("hls/contents/" + CONTENT_ID);
        seedRendition("hls/episodes/" + EPISODE_ID);
    }

    @Test
    @DisplayName("the master playlist keeps its tags and points variants back at this service")
    void masterIsRewrittenToOurEndpoints() throws Exception {
        UUID sessionId = session(null);

        mockMvc.perform(get("/api/v1/playback/stream/" + sessionId + "/master.m3u8").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("#EXTM3U")))
            .andExpect(content().string(containsString("#EXT-X-STREAM-INF:BANDWIDTH=464000")))
            .andExpect(content().string(containsString(
                "/api/v1/playback/stream/" + sessionId + "/240p/playlist.m3u8")));
    }

    @Test
    @DisplayName("a variant playlist embeds short-lived presigned URLs for its segments")
    void variantEmbedsPresignedSegments() throws Exception {
        UUID sessionId = session(null);

        String body = mockMvc.perform(get("/api/v1/playback/stream/" + sessionId + "/240p/playlist.m3u8")
                .with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("#EXTINF:6.0,")))
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
            .contains("http://localhost:8090/minio/"
                + HLS_BUCKET + "/hls/contents/" + CONTENT_ID + "/240p/segment_00000.ts")
            .contains("X-Amz-Expires=60")
            .contains("X-Amz-Signature=")
            // The signed query is forwarded verbatim: re-encoding it turns %2F
            // into %252F and MinIO rejects the request (caught in the E2E).
            .contains("X-Amz-Credential=zynema-admin%2F")
            .doesNotContain("%252F")
            .doesNotContain("\nsegment_00000.ts");
    }

    @Test
    @DisplayName("an episode session streams the episode's rendition, not its series'")
    void episodeSessionsUseTheEpisodeRendition() throws Exception {
        UUID sessionId = session(EPISODE_ID);

        String body = mockMvc.perform(get("/api/v1/playback/stream/" + sessionId + "/240p/playlist.m3u8")
                .with(asDemo()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
            .contains("/hls/episodes/" + EPISODE_ID + "/240p/segment_00000.ts")
            .doesNotContain("/hls/contents/");
    }

    @Test
    @DisplayName("a session that belongs to somebody else is invisible")
    void foreignSessionsAreNotFound() throws Exception {
        UUID foreign = sessionFor(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/playback/stream/" + foreign + "/master.m3u8").with(asDemo()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a title without a rendition is 409: the state is 'not ready', not 'broken'")
    void titlesWithoutRenditionsAreConflicts() throws Exception {
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/catalog/contents/" + CONTENT_ID))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "title": "Arcane", "type": "SERIES", "runtimeMinutes": 40, "hlsPath": null}
                    """.formatted(CONTENT_ID))));
        UUID sessionId = session(null);

        mockMvc.perform(get("/api/v1/playback/stream/" + sessionId + "/master.m3u8").with(asDemo()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("a variant that was never rendered is a 404, and a hostile name cannot escape the prefix")
    void unknownOrHostileVariantsAreNotFound() throws Exception {
        UUID sessionId = session(null);

        mockMvc.perform(get("/api/v1/playback/stream/" + sessionId + "/999p/playlist.m3u8").with(asDemo()))
            .andExpect(status().isNotFound());

        // A traversal attempt dies at the container (encoded slash = 400) or at
        // the variant whitelist (404). Either way it never reaches storage.
        mockMvc.perform(get("/api/v1/playback/stream/" + sessionId + "/..%2F..%2Fmaster/playlist.m3u8")
                .with(asDemo()))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("manifests are private: no token, no playlist")
    void manifestsRequireAToken() throws Exception {
        UUID sessionId = session(null);

        mockMvc.perform(get("/api/v1/playback/stream/" + sessionId + "/master.m3u8"))
            .andExpect(status().isUnauthorized());
    }

    // ───────────────────────────── helpers ────────────────────────────

    private void seedRendition(String prefix) {
        seedObject(prefix + "/master.m3u8", MASTER, "application/vnd.apple.mpegurl");
        seedObject(prefix + "/240p/playlist.m3u8", VARIANT, "application/vnd.apple.mpegurl");
        seedObject(prefix + "/240p/segment_00000.ts", "segment-bytes", "video/mp2t");
    }

    private UUID session(UUID episodeId) {
        return sessionFor(UUID.fromString(DEMO_USER), episodeId);
    }

    private UUID sessionFor(UUID userId) {
        return sessionFor(userId, null);
    }

    private UUID sessionFor(UUID userId, UUID episodeId) {
        PlaybackSession session = new PlaybackSession();
        session.setUserId(userId);
        session.setProfileId(UUID.fromString(DEMO_PROFILE));
        session.setContentId(CONTENT_ID);
        session.setEpisodeId(episodeId);
        session.setContentTitle("Arcane");
        session.setStatus(SessionStatus.STARTED);
        return sessionRepository.save(session).getId();
    }

    private static RequestPostProcessor asDemo() {
        return jwt().jwt(jwt -> jwt.subject(DEMO_SUBJECT).claim("email", "demo@zynema.dev"))
            .authorities(new SimpleGrantedAuthority("ROLE_user"));
    }
}
