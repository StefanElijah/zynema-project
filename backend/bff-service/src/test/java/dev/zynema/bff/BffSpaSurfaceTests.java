package dev.zynema.bff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * The BFF as the single surface for the SPA (ADR-0004): browse, search, the
 * pricing page, profile management, the watchlist, the session lifecycle and
 * checkout.
 *
 * <p>What these tests pin down is the <strong>contract the SPA will code
 * against</strong>, plus the two things a pass-through can silently break: the
 * token/correlation headers still travel downstream, and upstream statuses with
 * meaning — 201 vs 200 on a replayed checkout, 402 on the paywall — survive the
 * hop instead of being flattened to 200 or 500.
 */
class BffSpaSurfaceTests extends AbstractBffIntegrationTest {

    // ────────────────────────── browse & search ────────────────────────────

    @Test
    @DisplayName("browse forwards the filters as they came and maps the page for the grid")
    void browseForwardsFilters() {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/movies"))
            .willReturn(okJson(Stubs.cataloguePage(
                Stubs.catalogSummary(DUNE, "MOVIE", "Dune", "dune", 2021)))));

        client.get().uri(builder -> builder.path("/api/v1/web/catalog")
                .queryParam("type", "MOVIE")
                .queryParam("genre", "drama")
                .queryParam("yearFrom", "2000")
                .queryParam("yearTo", "2020")
                .queryParam("minRating", "7.5")
                .queryParam("sort", "rating,desc")
                .queryParam("page", "1")
                .queryParam("size", "24")
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].title").isEqualTo("Dune")
            .jsonPath("$.totalElements").isEqualTo(1)
            .jsonPath("$.first").isEqualTo(true);

        CATALOG.verify(getRequestedFor(urlPathEqualTo("/api/v1/catalog/movies"))
            .withQueryParam("genre", equalTo("drama"))
            .withQueryParam("yearFrom", equalTo("2000"))
            .withQueryParam("yearTo", equalTo("2020"))
            .withQueryParam("minRating", equalTo("7.5"))
            .withQueryParam("page", equalTo("1"))
            .withQueryParam("size", equalTo("24")));
    }

    @Test
    @DisplayName("search is public and the query travels to the catalogue")
    void searchIsPublic() {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/search"))
            .willReturn(okJson(Stubs.cataloguePage(
                Stubs.catalogSummary(ARCANE, "SERIES", "Arcane", "arcane", 2021)))));

        client.get().uri("/api/v1/web/catalog/search?q=arcane")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].title").isEqualTo("Arcane");

        CATALOG.verify(getRequestedFor(urlPathEqualTo("/api/v1/catalog/search"))
            .withQueryParam("q", equalTo("arcane")));
    }

    // ─────────────────────────── pricing & checkout ────────────────────────

    @Test
    @DisplayName("the pricing page is public and shows what the plans cost")
    void plansArePublic() {
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/plans"))
            .willReturn(okJson(Stubs.planList())));

        client.get().uri("/api/v1/web/plans")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[1].code").isEqualTo("standard")
            .jsonPath("$[1].price").isEqualTo(9.99)
            .jsonPath("$[1].maxStreams").isEqualTo(2);
    }

    @Test
    @DisplayName("checkout keeps the key idempotent and the created status intact")
    void checkoutForwardsTheIdempotencyKey() {
        PAYMENT.stubFor(post(urlPathEqualTo("/api/v1/payments/subscriptions"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(Stubs.subscriptionCreated())));

        client.mutateWith(asDemo())
            .post().uri("/api/v1/web/subscriptions")
            .header("Idempotency-Key", "spa-key-1")
            .bodyValue(Map.of("planId", "81000000-0000-4000-8000-000000000002",
                "paymentMethod", "card"))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ACTIVE")
            .jsonPath("$.plan.code").isEqualTo("standard");

        // The key the SPA generated is the key payment enforces the retry with:
        // it is forwarded untouched, and the token travels with it.
        PAYMENT.verify(postRequestedFor(urlPathEqualTo("/api/v1/payments/subscriptions"))
            .withHeader("Idempotency-Key", equalTo("spa-key-1"))
            .withHeader("Authorization", equalTo("Bearer " + TEST_TOKEN))
            .withRequestBody(matchingJsonPath("$.planId",
                equalTo("81000000-0000-4000-8000-000000000002"))));
    }

    @Test
    @DisplayName("a replayed checkout answers 200, because the client is entitled to know")
    void replayedCheckoutKeepsTheUpstreamStatus() {
        PAYMENT.stubFor(post(urlPathEqualTo("/api/v1/payments/subscriptions"))
            .willReturn(okJson(Stubs.subscriptionCreated())));

        client.mutateWith(asDemo())
            .post().uri("/api/v1/web/subscriptions")
            .header("Idempotency-Key", "spa-key-1")
            .bodyValue(Map.of("planId", "81000000-0000-4000-8000-000000000002"))
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    @DisplayName("checkout without a key is rejected before reaching payment")
    void checkoutWithoutKeyIsRejected() {
        client.mutateWith(asDemo())
            .post().uri("/api/v1/web/subscriptions")
            .bodyValue(Map.of("planId", "81000000-0000-4000-8000-000000000002"))
            .exchange()
            .expectStatus().isBadRequest();

        PAYMENT.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/payments/subscriptions")));
    }

    // ────────────────────────── profiles & watchlist ───────────────────────

    @Test
    @DisplayName("profiles need a token, and the token is what user-service sees")
    void profilesNeedAToken() {
        client.get().uri("/api/v1/web/profiles")
            .exchange()
            .expectStatus().isUnauthorized();

        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me/profiles"))
            .willReturn(okJson(Stubs.profiles(
                Stubs.profile(DEMO_PROFILE, "Demo", false),
                Stubs.profile("72000000-0000-4000-8000-000000000002", "Kids", true)))));

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/profiles")
            .header("X-Correlation-Id", "profiles-corr")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[1].name").isEqualTo("Kids")
            .jsonPath("$[1].kids").isEqualTo(true);

        USER.verify(getRequestedFor(urlPathEqualTo("/api/v1/users/me/profiles"))
            .withHeader("Authorization", equalTo("Bearer " + TEST_TOKEN))
            .withHeader("X-Correlation-Id", equalTo("profiles-corr")));
    }

    @Test
    @DisplayName("a profile is created and deleted through the same surface")
    void profileWritesArePassThrough() {
        USER.stubFor(post(urlPathEqualTo("/api/v1/users/me/profiles"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(Stubs.profile("72000000-0000-4000-8000-000000000003", "Kids", true))));
        USER.stubFor(delete(urlPathEqualTo("/api/v1/users/me/profiles/72000000-0000-4000-8000-000000000003"))
            .willReturn(aResponse().withStatus(204)));

        client.mutateWith(asDemo())
            .post().uri("/api/v1/web/profiles")
            .bodyValue(Map.of("name", "Kids", "kids", true, "language", "es"))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Kids");

        client.mutateWith(asDemo())
            .delete().uri("/api/v1/web/profiles/72000000-0000-4000-8000-000000000003")
            .exchange()
            .expectStatus().isNoContent();

        USER.verify(postRequestedFor(urlPathEqualTo("/api/v1/users/me/profiles"))
            .withRequestBody(matchingJsonPath("$.name", equalTo("Kids"))));
        USER.verify(deleteRequestedFor(
            urlPathEqualTo("/api/v1/users/me/profiles/72000000-0000-4000-8000-000000000003")));
    }

    @Test
    @DisplayName("the watchlist is added to and removed from with the same endpoint pair")
    void watchlistAddAndRemove() {
        USER.stubFor(post(urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody(Stubs.watchlistEntry(DUNE))));
        USER.stubFor(delete(urlPathEqualTo(
            "/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist/" + DUNE))
            .willReturn(aResponse().withStatus(204)));

        client.mutateWith(asDemo())
            .post().uri("/api/v1/web/profiles/" + DEMO_PROFILE + "/watchlist")
            .bodyValue(Map.of("contentId", DUNE))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.contentId").isEqualTo(DUNE);

        client.mutateWith(asDemo())
            .delete().uri("/api/v1/web/profiles/" + DEMO_PROFILE + "/watchlist/" + DUNE)
            .exchange()
            .expectStatus().isNoContent();

        USER.verify(postRequestedFor(urlPathEqualTo(
            "/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist"))
            .withRequestBody(matchingJsonPath("$.contentId", equalTo(DUNE))));
    }

    // ─────────────────────────── playback sessions ─────────────────────────

    @Test
    @DisplayName("the player's whole session lifecycle passes through with its headers")
    void sessionLifecycleIsPassThrough() {
        String sessionId = "76000000-0000-4000-8000-000000000001";
        String streamPath = "/api/v1/playback/streams/" + sessionId + "/master.m3u8";
        PLAYBACK.stubFor(post(urlPathEqualTo("/api/v1/playback/sessions"))
            .willReturn(okJson(Stubs.session(sessionId, "STARTED", 0, streamPath))));
        PLAYBACK.stubFor(put(urlPathEqualTo("/api/v1/playback/sessions/" + sessionId + "/position"))
            .willReturn(okJson(Stubs.session(sessionId, "ACTIVE", 600, streamPath))));
        PLAYBACK.stubFor(post(urlPathEqualTo("/api/v1/playback/sessions/" + sessionId + "/end"))
            .willReturn(okJson(Stubs.session(sessionId, "ENDED", 2400, streamPath))));
        PLAYBACK.stubFor(get(urlPathEqualTo("/api/v1/playback/sessions/me/active"))
            .willReturn(okJson("[" + Stubs.session(sessionId, "ACTIVE", 600, streamPath) + "]")));

        client.mutateWith(asDemo())
            .post().uri("/api/v1/web/playback/sessions")
            .header("X-Correlation-Id", "play-corr")
            .bodyValue(Map.of("profileId", DEMO_PROFILE, "contentId", ARCANE, "device", "web"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.streamPath").isEqualTo(streamPath)
            .jsonPath("$.status").isEqualTo("STARTED");

        client.mutateWith(asDemo())
            .put().uri("/api/v1/web/playback/sessions/" + sessionId + "/position")
            .bodyValue(Map.of("positionSeconds", 600))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.positionSeconds").isEqualTo(600);

        client.mutateWith(asDemo())
            .post().uri("/api/v1/web/playback/sessions/" + sessionId + "/end")
            .bodyValue(Map.of("positionSeconds", 2400))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("ENDED");

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/playback/sessions/me/active")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1);

        PLAYBACK.verify(postRequestedFor(urlPathEqualTo("/api/v1/playback/sessions"))
            .withHeader("Authorization", equalTo("Bearer " + TEST_TOKEN))
            .withHeader("X-Correlation-Id", equalTo("play-corr"))
            .withRequestBody(matchingJsonPath("$.profileId", equalTo(DEMO_PROFILE))));
        PLAYBACK.verify(putRequestedFor(
            urlPathEqualTo("/api/v1/playback/sessions/" + sessionId + "/position"))
            .withRequestBody(matchingJsonPath("$.positionSeconds", equalTo("600"))));
    }

    @Test
    @DisplayName("the paywall's 402 crosses the BFF with its message")
    void paywallStatusAndMessageSurvive() {
        PLAYBACK.stubFor(post(urlPathEqualTo("/api/v1/playback/sessions"))
            .willReturn(aResponse().withStatus(402)
                .withHeader("Content-Type", "application/json")
                .withBody(Stubs.error(402, "Payment Required",
                    "An active subscription is required to start playback"))));

        client.mutateWith(asDemo())
            .post().uri("/api/v1/web/playback/sessions")
            .bodyValue(Map.of("profileId", DEMO_PROFILE, "contentId", ARCANE))
            .exchange()
            .expectStatus().isEqualTo(402)
            .expectBody()
            .jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("active subscription"));
    }

    @Test
    @DisplayName("when playback is down the player gets a 503 that names the dependency")
    void playbackDownIsAPlatformError() {
        PLAYBACK.stubFor(post(urlPathEqualTo("/api/v1/playback/sessions"))
            .willReturn(aResponse().withStatus(500)));

        client.mutateWith(asDemo())
            .post().uri("/api/v1/web/playback/sessions")
            .bodyValue(Map.of("profileId", DEMO_PROFILE, "contentId", ARCANE))
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.message").value(
                org.hamcrest.Matchers.containsString("playback-service"));
    }
}
