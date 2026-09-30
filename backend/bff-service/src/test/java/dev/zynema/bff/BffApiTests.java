package dev.zynema.bff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * What the frontend gets, and who is allowed to ask for it.
 *
 * <p>The access model under test: browsing is public, personal screens need a
 * token, and the answer about watching is always an entitlement the BFF was
 * told — the BFF never decides it.
 */
class BffApiTests extends AbstractBffIntegrationTest {

    // ─────────────────────────────── home ──────────────────────────────

    @Test
    @DisplayName("the landing page composes a hero and three rails without a token")
    void homeIsPublicAndComposesRails() {
        stubCatalogueLists();

        client.get().uri("/api/v1/web/home")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.hero.card.title").isEqualTo("Arcane")
            .jsonPath("$.hero.synopsis").isEqualTo("In the cities of Piltover and Zaun.")
            .jsonPath("$.rows.length()").isEqualTo(3)
            .jsonPath("$.rows[0].id").isEqualTo("new-releases")
            .jsonPath("$.rows[1].id").isEqualTo("popular-series")
            .jsonPath("$.rows[1].items[0].title").isEqualTo("Arcane")
            .jsonPath("$.rows[2].id").isEqualTo("popular-movies")
            .jsonPath("$.rows[2].items[0].genres[0].slug").isEqualTo("drama");
    }

    @Test
    @DisplayName("the landing page is served from the cache: one downstream round per TTL")
    void homeIsCachedAsAWhole() {
        stubCatalogueLists();

        client.get().uri("/api/v1/web/home").exchange().expectStatus().isOk();
        client.get().uri("/api/v1/web/home").exchange().expectStatus().isOk();

        CATALOG.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/catalog/movies"))
            .withQueryParam("sort", equalTo("releaseYear,desc")));
        CATALOG.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/catalog/contents/" + ARCANE)));
    }

    @Test
    @DisplayName("when the catalogue is down the landing page says 503, it does not render empty")
    void catalogueDownFailsTheLandingPage() {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/movies"))
            .willReturn(aResponse().withStatus(503)));
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/series"))
            .willReturn(aResponse().withStatus(503)));

        // The four parallel rails fail, retry a few times and then whichever
        // error wins the race (the last 5xx or the now-open breaker) is mapped
        // to 503 for the same dependency; the message names it either way.
        client.get().uri("/api/v1/web/home")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.message").value(org.hamcrest.Matchers.containsString("catalog-service"));
    }

    // ───────────────────────────── detail ──────────────────────────────

    @Test
    @DisplayName("an anonymous visitor sees the detail and is told to sign in to watch")
    void detailWithoutTokenAsksForLogin() {
        stubDetail(ARCANE, "SERIES", "Arcane", "arcane");

        client.get().uri("/api/v1/web/catalog/" + ARCANE)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.title").isEqualTo("Arcane")
            .jsonPath("$.content.seasons[0].episodeCount").isEqualTo(9)
            .jsonPath("$.content.credits[0].personName").isEqualTo("Hailee Steinfeld")
            .jsonPath("$.playback.allowed").isEqualTo(false)
            .jsonPath("$.playback.reason").isEqualTo("AUTHENTICATION_REQUIRED")
            .jsonPath("$.progress").doesNotExist()
            .jsonPath("$.degraded.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("a subscriber can watch, and the token travels to the services that decide it")
    void detailWithPlanAllowsPlayback() {
        stubDetail(ARCANE, "SERIES", "Arcane", "arcane");
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .willReturn(okJson(Stubs.entitlements(true, 2))));

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/catalog/" + ARCANE)
            .header("X-Correlation-Id", "test-correlation")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.playback.allowed").isEqualTo(true)
            .jsonPath("$.playback.reason").doesNotExist();

        // The token is relayed, not minted: payment sees the same user the BFF did.
        PAYMENT.verify(getRequestedFor(urlPathEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .withHeader("Authorization", equalTo("Bearer " + TEST_TOKEN))
            .withHeader("X-Correlation-Id", equalTo("test-correlation")));
    }

    @Test
    @DisplayName("without a plan the answer is the paywall, not a denial or an error")
    void detailWithoutPlanShowsThePaywall() {
        stubDetail(ARCANE, "SERIES", "Arcane", "arcane");
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .willReturn(okJson(Stubs.entitlements(false, 1))));

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/catalog/" + ARCANE)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.playback.allowed").isEqualTo(false)
            .jsonPath("$.playback.reason").isEqualTo("SUBSCRIPTION_REQUIRED");
    }

    @Test
    @DisplayName("a slug is resolved without the frontend knowing which endpoint owns it")
    void detailAcceptsASlug() {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/movies/arcane"))
            .willReturn(aResponse().withStatus(404)));
        stubDetailPath("/api/v1/catalog/series/arcane", ARCANE, "SERIES", "Arcane", "arcane");

        client.get().uri("/api/v1/web/catalog/arcane")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.title").isEqualTo("Arcane");
    }

    @Test
    @DisplayName("an unknown title is a 404 with the shared envelope")
    void unknownTitleIsNotFound() {
        client.get().uri("/api/v1/web/catalog/a1000000-0000-4000-8000-0000000000ff")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.status").isEqualTo(404);
    }

    // ───────────────────────────── account ─────────────────────────────

    @Test
    @DisplayName("the account view merges token claims, local account and plan in one call")
    void accountComposesEverything() {
        stubUserAccount();
        stubSubscription();

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/account")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.identity.subject").isEqualTo(DEMO_SUBJECT)
            .jsonPath("$.identity.roles[0]").isEqualTo("user")
            .jsonPath("$.user.email").isEqualTo("demo@zynema.dev")
            .jsonPath("$.user.profiles[0].id").isEqualTo(DEMO_PROFILE)
            .jsonPath("$.subscription.planCode").isEqualTo("standard")
            .jsonPath("$.subscription.status").isEqualTo("ACTIVE")
            .jsonPath("$.entitlements.active").isEqualTo(true)
            .jsonPath("$.entitlements.maxStreams").isEqualTo(2)
            .jsonPath("$.degraded.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("the account view is not reachable without a token")
    void accountRequiresAToken() {
        client.get().uri("/api/v1/web/account")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // ────────────────────────── profile rails ──────────────────────────

    @Test
    @DisplayName("continue watching and my list come back joined with the catalogue")
    void profileHomeJoinsRailsWithTheCatalogue() {
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/continue-watching"))
            .willReturn(okJson(Stubs.continueWatching(ARCANE))));
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist"))
            .willReturn(okJson(Stubs.watchlist(DUNE))));
        stubDetail(ARCANE, "SERIES", "Arcane", "arcane");
        stubDetail(DUNE, "MOVIE", "Dune: Part Two", "dune-part-two");

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/profiles/" + DEMO_PROFILE + "/home")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.profileId").isEqualTo(DEMO_PROFILE)
            .jsonPath("$.continueWatching[0].content.title").isEqualTo("Arcane")
            .jsonPath("$.continueWatching[0].positionSeconds").isEqualTo(600)
            .jsonPath("$.myList[0].content.title").isEqualTo("Dune: Part Two")
            .jsonPath("$.degraded.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("the profile rails are not reachable without a token")
    void profileHomeRequiresAToken() {
        client.get().uri("/api/v1/web/profiles/" + DEMO_PROFILE + "/home")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    // ────────────────────────────── helpers ────────────────────────────

    private void stubCatalogueLists() {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/series"))
            .willReturn(okJson(Stubs.cataloguePage(
                Stubs.catalogSummary(ARCANE, "SERIES", "Arcane", "arcane", 2021)))));
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/movies"))
            .willReturn(okJson(Stubs.cataloguePage(
                Stubs.catalogSummary(DUNE, "MOVIE", "Dune: Part Two", "dune-part-two", 2024)))));
        stubDetail(ARCANE, "SERIES", "Arcane", "arcane");
    }

    private void stubDetail(String id, String type, String title, String slug) {
        stubDetailPath("/api/v1/catalog/contents/" + id, id, type, title, slug);
    }

    private void stubDetailPath(String path, String id, String type, String title, String slug) {
        CATALOG.stubFor(get(urlPathEqualTo(path))
            .willReturn(okJson(Stubs.catalogDetail(id, type, title, slug,
                "In the cities of Piltover and Zaun."))));
    }

    private void stubUserAccount() {
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me"))
            .willReturn(okJson(Stubs.userAccount())));
    }

    private void stubSubscription() {
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/subscriptions/me"))
            .willReturn(okJson(Stubs.subscription())));
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .willReturn(okJson(Stubs.entitlements(true, 2))));
    }
}
