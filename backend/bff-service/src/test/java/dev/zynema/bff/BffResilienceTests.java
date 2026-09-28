package dev.zynema.bff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

/**
 * How composed screens behave when a dependency is not available.
 *
 * <p>The contract: a screen that cannot be built at all fails with 503; a
 * screen that is missing one section still renders and names the section in
 * {@code degraded}. The tests below pin both halves — including that a visitor
 * without a plan is <em>not</em> degraded, because "no" is an answer.
 */
class BffResilienceTests extends AbstractBffIntegrationTest {

    @Test
    @DisplayName("a payment outage degrades the subscription section, not the account view")
    void paymentOutageDegradesOnlyTheSubscription() {
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me"))
            .willReturn(okJson(Stubs.userAccount())));
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/subscriptions/me"))
            .willReturn(aResponse().withStatus(503)));
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .willReturn(aResponse().withStatus(503)));

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/account")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.user.email").isEqualTo("demo@zynema.dev")
            .jsonPath("$.degraded", contains("SUBSCRIPTION"));
    }

    @Test
    @DisplayName("a visitor without a plan is not degraded: the paywall is an answer")
    void noSubscriptionIsNotADegradation() {
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me"))
            .willReturn(okJson(Stubs.userAccount())));
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/subscriptions/me"))
            .willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":404,\"message\":\"No active subscription\"}")));
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .willReturn(okJson(Stubs.entitlements(false, 1))));

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/account")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.degraded.length()").isEqualTo(0)
            .jsonPath("$.entitlements.active").isEqualTo(false);
    }

    @Test
    @DisplayName("when the plan cannot be checked the detail says so instead of guessing")
    void entitlementsOutageMarksPlaybackUnavailable() {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/contents/" + ARCANE))
            .willReturn(okJson(Stubs.catalogDetail(ARCANE, "SERIES", "Arcane", "arcane", "Synopsis."))));
        PAYMENT.stubFor(get(urlPathEqualTo("/api/v1/payments/subscriptions/me/entitlements"))
            .willReturn(aResponse().withStatus(503)));

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/catalog/" + ARCANE)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.playback.allowed").isEqualTo(false)
            .jsonPath("$.playback.reason").isEqualTo("UNAVAILABLE")
            .jsonPath("$.degraded", contains("PLAYBACK"));
    }

    @Test
    @DisplayName("a failing rail does not hide the other one")
    void railsDegradeIndependently() {
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/continue-watching"))
            .willReturn(aResponse().withStatus(503)));
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist"))
            .willReturn(okJson(Stubs.watchlist(DUNE))));
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/contents/" + DUNE))
            .willReturn(okJson(Stubs.catalogDetail(DUNE, "MOVIE", "Dune: Part Two", "dune-part-two", "Synopsis."))));

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/profiles/" + DEMO_PROFILE + "/home")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.continueWatching.length()").isEqualTo(0)
            .jsonPath("$.myList[0].content.title").isEqualTo("Dune: Part Two")
            .jsonPath("$.degraded", contains("CONTINUE_WATCHING"));
    }

    @Test
    @DisplayName("a title that disappeared from the catalogue is dropped, not fatal")
    void staleRailEntryIsDropped() {
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/continue-watching"))
            .willReturn(okJson(Stubs.continueWatching(ARCANE))));
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist"))
            .willReturn(okJson("[]")));
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/contents/" + ARCANE))
            .willReturn(aResponse().withStatus(404)));

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/profiles/" + DEMO_PROFILE + "/home")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.continueWatching.length()").isEqualTo(0)
            .jsonPath("$.degraded.length()").isEqualTo(0);
    }

    @Test
    @DisplayName("when the catalogue is down the rails fail too: no card can be built")
    void catalogueDownFailsTheRails() {
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/continue-watching"))
            .willReturn(okJson(Stubs.continueWatching(ARCANE))));
        USER.stubFor(get(urlPathEqualTo("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist"))
            .willReturn(okJson("[]")));
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/contents/" + ARCANE))
            .willReturn(aResponse().withStatus(503)));

        client.mutateWith(asDemo())
            .get().uri("/api/v1/web/profiles/" + DEMO_PROFILE + "/home")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.details.service").isEqualTo("catalog-service");
    }

    @Test
    @DisplayName("a cached answer survives the dependency going down")
    void cacheServesWhileTheDependencyIsDown() {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/contents/" + ARCANE))
            .willReturn(okJson(Stubs.catalogDetail(ARCANE, "SERIES", "Arcane", "arcane", "Synopsis."))));

        client.get().uri("/api/v1/web/catalog/" + ARCANE)
            .exchange()
            .expectStatus().isOk();

        CATALOG.resetAll(); // the catalogue disappears after the first call

        client.get().uri("/api/v1/web/catalog/" + ARCANE)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content.title", is("Arcane"));
    }
}
