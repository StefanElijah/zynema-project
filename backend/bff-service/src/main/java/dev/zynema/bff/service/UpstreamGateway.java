package dev.zynema.bff.service;

import dev.zynema.bff.client.CatalogClient;
import dev.zynema.bff.client.PaymentClient;
import dev.zynema.bff.client.UserClient;
import dev.zynema.bff.config.BffCacheConfig;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Every downstream call, with its resilience policy in one place.
 *
 * <p>Composition, from outside in: {@code @Retry( @CircuitBreaker( @Bulkhead( call ) ) )},
 * the same shape the blocking domain services use. What is different in the
 * BFF is <strong>where the fallback lives</strong>:
 * <ul>
 *   <li><b>Required</b> calls (catalogue, the identity behind {@code /account})
 *       have none. A screen without content or without an account is not a
 *       degraded screen, it is an error — the caller gets a 503 and retries.</li>
 *   <li><b>Optional</b> calls (entitlements, subscription, the rails) fall back
 *       to {@link DownstreamResult#degraded()}, and the view layer reports the
 *       missing section. The fallback sits on {@code @Retry}, the outermost
 *       aspect, so transient failures are still retried before giving up.</li>
 * </ul>
 *
 * <p>The calls return raw WebClient publishers on purpose: retry and the
 * circuit breaker classify the real HTTP outcome (a 404 is not a failure,
 * a refused connection is), and the error is translated to the API contract
 * only after the call has definitively given up — see
 * {@link dev.zynema.bff.client.DownstreamErrors}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpstreamGateway {

    private final CatalogClient catalog;
    private final UserClient user;
    private final PaymentClient payment;

    // ───────────────────────────── catalogue ─────────────────────────────

    @Retry(name = "catalog-service")
    @CircuitBreaker(name = "catalog-service")
    @Bulkhead(name = "catalog-service")
    public Mono<List<CatalogClient.ContentSummary>> listMovies(String sort, int size) {
        return catalog.listMovies(sort, size);
    }

    @Retry(name = "catalog-service")
    @CircuitBreaker(name = "catalog-service")
    @Bulkhead(name = "catalog-service")
    public Mono<List<CatalogClient.ContentSummary>> listSeries(String sort, int size) {
        return catalog.listSeries(sort, size);
    }

    /**
     * Cached: the detail payload is user-independent, so one entry serves the
     * detail screen and every rail that needs the same title. The cache aspect
     * is ordered outside the resilience aspects, so a hit is served even while
     * the catalogue's circuit is open.
     */
    @Cacheable(cacheNames = BffCacheConfig.CONTENT, key = "#idOrSlug")
    @Retry(name = "catalog-service")
    @CircuitBreaker(name = "catalog-service")
    @Bulkhead(name = "catalog-service")
    public Mono<CatalogClient.ContentDetail> contentDetail(String idOrSlug) {
        return catalog.contentDetail(idOrSlug);
    }

    // ──────────────────────────── user account ───────────────────────────

    @Retry(name = "user-service")
    @CircuitBreaker(name = "user-service")
    @Bulkhead(name = "user-service")
    public Mono<UserClient.UserAccount> currentUser() {
        return user.me();
    }

    /**
     * The rails are content for a page that still renders without them: a
     * failure removes one rail and says so, instead of taking the screen down.
     */
    @Retry(name = "user-service", fallbackMethod = "continueWatchingUnavailable")
    @CircuitBreaker(name = "user-service")
    @Bulkhead(name = "user-service")
    public Mono<DownstreamResult<List<UserClient.WatchHistory>>> continueWatching(UUID profileId) {
        return user.continueWatching(profileId).map(DownstreamResult::of);
    }

    @Retry(name = "user-service", fallbackMethod = "watchlistUnavailable")
    @CircuitBreaker(name = "user-service")
    @Bulkhead(name = "user-service")
    public Mono<DownstreamResult<List<UserClient.WatchlistEntry>>> watchlist(UUID profileId) {
        return user.watchlist(profileId).map(DownstreamResult::of);
    }

    Mono<DownstreamResult<List<UserClient.WatchHistory>>> continueWatchingUnavailable(UUID profileId, Throwable failure) {
        log.warn("Continue-watching rail unavailable for profile {}: {}", profileId, failure.getMessage());
        return Mono.just(DownstreamResult.unavailable());
    }

    Mono<DownstreamResult<List<UserClient.WatchlistEntry>>> watchlistUnavailable(UUID profileId, Throwable failure) {
        log.warn("Watchlist rail unavailable for profile {}: {}", profileId, failure.getMessage());
        return Mono.just(DownstreamResult.unavailable());
    }

    // ───────────────────────────── payments ──────────────────────────────

    @Retry(name = "payment-service", fallbackMethod = "subscriptionUnavailable")
    @CircuitBreaker(name = "payment-service")
    @Bulkhead(name = "payment-service")
    public Mono<DownstreamResult<PaymentClient.Subscription>> subscription() {
        return payment.currentSubscription()
            .map(DownstreamResult::of)
            // No subscription is a valid state, not a failure: the account
            // screen shows the paywall instead of a warning.
            .onErrorResume(UpstreamGateway::isNotFound, missing -> Mono.just(DownstreamResult.absent()));
    }

    @Retry(name = "payment-service", fallbackMethod = "entitlementsUnavailable")
    @CircuitBreaker(name = "payment-service")
    @Bulkhead(name = "payment-service")
    public Mono<DownstreamResult<PaymentClient.Entitlements>> entitlements() {
        return payment.entitlements().map(DownstreamResult::of);
    }

    Mono<DownstreamResult<PaymentClient.Subscription>> subscriptionUnavailable(Throwable failure) {
        log.warn("Subscription unavailable: {}", failure.getMessage());
        return Mono.just(DownstreamResult.unavailable());
    }

    Mono<DownstreamResult<PaymentClient.Entitlements>> entitlementsUnavailable(Throwable failure) {
        log.warn("Entitlements unavailable: {}", failure.getMessage());
        return Mono.just(DownstreamResult.unavailable());
    }

    private static boolean isNotFound(Throwable failure) {
        return failure instanceof org.springframework.web.reactive.function.client.WebClientResponseException response
            && response.getStatusCode().value() == 404;
    }
}
