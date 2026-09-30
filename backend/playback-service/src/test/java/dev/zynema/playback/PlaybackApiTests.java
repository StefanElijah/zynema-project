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
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Playback contract: session lifecycle, the plan's concurrency limit and how
 * the service degrades when its dependencies are unhealthy.
 */
@AutoConfigureMockMvc
class PlaybackApiTests extends AbstractPlaybackIntegrationTest {

    private static final String DEMO_SUBJECT = "11111111-1111-4111-8111-111111111111";
    private static final String DEMO_USER = "71000000-0000-4000-8000-000000000001";
    private static final String DEMO_PROFILE = "72000000-0000-4000-8000-000000000001";
    private static final String ARCADE_CONTENT = "a2000000-0000-4000-8000-000000000002";
    private static final String DUNE_CONTENT = "a1000000-0000-4000-8000-000000000003";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlaybackSessionRepository sessionRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void reset() {
        sessionRepository.deleteAll();
        resetEventStore();
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
        DEPENDENCIES.resetAll();
        stubUserService();
        stubCatalog();
        stubPayment(1);
    }

    // ────────────────────────────── start ──────────────────────────────

    @Test
    @DisplayName("starting a title validates it against catalog and opens a session")
    void startOpensASession() throws Exception {
        mockMvc.perform(post("/api/v1/playback/sessions").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(DEMO_PROFILE, ARCADE_CONTENT, null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contentTitle", is("Arcane")))
            .andExpect(jsonPath("$.status", is("STARTED")))
            .andExpect(jsonPath("$.durationSeconds", is(2400)))
            // The client gets the HLS entry point with the session, so it does
            // not have to guess the URL shape (ADR-0024).
            .andExpect(jsonPath("$.streamPath", org.hamcrest.Matchers.matchesPattern(
                "/api/v1/playback/stream/.+/master\\.m3u8")));

        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("playback requires a token")
    void startRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/playback/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(DEMO_PROFILE, ARCADE_CONTENT, null)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("starting the same title again resumes the open session")
    void startResumesTheOpenSession() throws Exception {
        String first = start(ARCADE_CONTENT, null, 1);
        String second = start(ARCADE_CONTENT, null, 1);

        assertThat(second).isEqualTo(first);
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown title is a 404")
    void unknownTitleIsNotFound() throws Exception {
        DEPENDENCIES.resetAll();
        stubUserService();
        stubPayment(1);
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlMatching("/api/v1/catalog/contents/.*"))
            .willReturn(WireMock.aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":404,\"message\":\"Content not found\"}")));

        mockMvc.perform(post("/api/v1/playback/sessions").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(DEMO_PROFILE, ARCADE_CONTENT, null)))
            .andExpect(status().isNotFound());
    }

    // ───────────────────────── concurrency limit ───────────────────────

    @Test
    @DisplayName("a plan that allows a single stream refuses the second one")
    void basicPlanAllowsOneStream() throws Exception {
        stubEntitlements(1);
        start(ARCADE_CONTENT, null, 1);

        mockMvc.perform(post("/api/v1/playback/sessions").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(DEMO_PROFILE, DUNE_CONTENT, null)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message", is("The plan allows 1 concurrent stream(s) and 1 are already open")));
    }

    @Test
    @DisplayName("a premium plan allows more concurrent streams")
    void premiumAllowsMoreStreams() throws Exception {
        stubEntitlements(4);
        start(ARCADE_CONTENT, null, 4);
        start(DUNE_CONTENT, null, 4);

        assertThat(sessionRepository.count()).isEqualTo(2);
    }

    // ──────────────────────────── paywall ──────────────────────────────

    @Test
    @DisplayName("watching without an active plan is a paywall, not a session")
    void withoutActivePlanIsPaymentRequired() throws Exception {
        stubEntitlements(false, 1);

        mockMvc.perform(post("/api/v1/playback/sessions").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(DEMO_PROFILE, ARCADE_CONTENT, null)))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.details.code", is("SUBSCRIPTION_REQUIRED")))
            .andExpect(jsonPath("$.message", is("An active subscription is required to start playback")));

        assertThat(sessionRepository.count()).isZero();
    }

    // ─────────────────────────── heartbeat/end ─────────────────────────

    @Test
    @DisplayName("a heartbeat records the position and forwards it to user-service")
    void heartbeatForwardsProgress() throws Exception {
        String sessionId = start(ARCADE_CONTENT, null, 1);
        DEPENDENCIES.resetRequests();

        mockMvc.perform(put("/api/v1/playback/sessions/" + sessionId + "/position").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionSeconds\": 900}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.positionSeconds", is(900)));

        DEPENDENCIES.verify(WireMock.putRequestedFor(
                WireMock.urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/progress"))
            .withRequestBody(WireMock.containing("\"positionSeconds\":900")));
    }

    @Test
    @DisplayName("ending a session closes it and keeps the final position")
    void endClosesTheSession() throws Exception {
        String sessionId = start(ARCADE_CONTENT, null, 1);

        mockMvc.perform(post("/api/v1/playback/sessions/" + sessionId + "/end").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionSeconds\": 2390}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ENDED")))
            .andExpect(jsonPath("$.positionSeconds", is(2390)));

        mockMvc.perform(get("/api/v1/playback/sessions/me/active").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("active sessions are listed newest first")
    void activeSessionsAreListed() throws Exception {
        start(ARCADE_CONTENT, null, 4);
        start(DUNE_CONTENT, null, 4);

        mockMvc.perform(get("/api/v1/playback/sessions/me/active").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].contentId", is(DUNE_CONTENT)));
    }

    @Test
    @DisplayName("a session of another account is invisible and does not appear in my list")
    void foreignSessionIsNotFound() throws Exception {
        // The stub always resolves the caller to DEMO_USER, so ownership is
        // exercised with a session that belongs to a different local account.
        PlaybackSession foreign = new PlaybackSession();
        foreign.setUserId(UUID.randomUUID());
        foreign.setProfileId(UUID.randomUUID());
        foreign.setContentId(UUID.fromString(ARCADE_CONTENT));
        foreign.setContentTitle("Someone else's show");
        foreign.setStatus(SessionStatus.STARTED);
        UUID foreignId = sessionRepository.save(foreign).getId();

        mockMvc.perform(put("/api/v1/playback/sessions/" + foreignId + "/position").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"positionSeconds\": 10}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/playback/sessions/me/active").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    // ─────────────────────────── resilience ───────────────────────────

    @Test
    @DisplayName("when payment is down playback refuses to guess the plan: 503, no session")
    void paymentDownRefusesToGuess() throws Exception {
        DEPENDENCIES.resetAll();
        stubUserService();
        stubCatalog();
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .willReturn(WireMock.aResponse().withStatus(503)));

        mockMvc.perform(post("/api/v1/playback/sessions").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(DEMO_PROFILE, DUNE_CONTENT, null)))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.details.service", is("payment-service")));

        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    @DisplayName("when catalog is down the API answers 503 instead of starting a session it cannot verify")
    void catalogDownIsServiceUnavailable() throws Exception {
        DEPENDENCIES.resetAll();
        stubUserService();
        stubPayment(1);
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlMatching("/api/v1/catalog/contents/.*"))
            .willReturn(WireMock.aResponse().withStatus(503)));

        mockMvc.perform(post("/api/v1/playback/sessions").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(DEMO_PROFILE, ARCADE_CONTENT, null)))
            .andExpect(status().isServiceUnavailable());
    }

    // ────────────────────────────── helpers ───────────────────────────

    private static RequestPostProcessor asDemo() {
        return jwt().jwt(jwt -> jwt.subject(DEMO_SUBJECT).claim("email", "demo@zynema.dev"))
            .authorities(new SimpleGrantedAuthority("ROLE_user"));
    }

    private String start(String contentId, UUID episodeId, int maxStreams) throws Exception {
        stubEntitlements(maxStreams);
        String body = mockMvc.perform(post("/api/v1/playback/sessions").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content(startBody(DEMO_PROFILE, contentId, episodeId)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }

    private String startBody(String profileId, String contentId, UUID episodeId) {
        return """
            {"profileId": "%s", "contentId": "%s", "episodeId": %s, "device": "test"}
            """.formatted(profileId, contentId, episodeId == null ? "null" : "\"" + episodeId + "\"");
    }

    private void stubUserService() {
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/users/me"))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "email": "demo@zynema.dev", "displayName": "Demo User"}
                    """.formatted(DEMO_USER))));
        DEPENDENCIES.stubFor(WireMock.put(WireMock.urlMatching("/api/v1/users/me/profiles/.*/progress"))
            .willReturn(WireMock.aResponse().withStatus(200)));
    }

    private void stubCatalog() {
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/catalog/contents/" + ARCADE_CONTENT))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "title": "Arcane", "type": "SERIES", "runtimeMinutes": 40}
                    """.formatted(ARCADE_CONTENT))));
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/catalog/contents/" + DUNE_CONTENT))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id": "%s", "title": "Dune: Part Two", "type": "MOVIE", "runtimeMinutes": 166}
                    """.formatted(DUNE_CONTENT))));
    }

    private void stubPayment(int maxStreams) {
        stubEntitlements(maxStreams);
    }

    private void stubEntitlements(int maxStreams) {
        stubEntitlements(true, maxStreams);
    }

    private void stubEntitlements(boolean active, int maxStreams) {
        DEPENDENCIES.stubFor(WireMock.get(WireMock.urlEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .willReturn(WireMock.aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"active": %s, "maxStreams": %d, "maxQuality": "FHD", "planCode": "standard", "validUntil": null}
                    """.formatted(active, maxStreams))));
    }
}
