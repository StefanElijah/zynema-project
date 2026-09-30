package dev.zynema.playback.service;

import dev.zynema.common.exception.DownstreamServiceException;
import dev.zynema.common.exception.ResourceNotFoundException;
import dev.zynema.playback.client.CatalogServiceClient;
import dev.zynema.playback.client.PaymentServiceClient;
import dev.zynema.playback.client.UserServiceClient;
import dev.zynema.playback.config.CacheConfig;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Everything playback needs from other services, with the resilience policy
 * applied in one place.
 *
 * <p>Composition, from outside in: {@code @Retry( @CircuitBreaker( @Bulkhead( call ) ) )}.
 * Timeouts come from the HTTP client (see the Feign read timeout), which is the
 * real bound for a blocking call.
 *
 * <p>Degradation is deliberate per dependency:
 * <ul>
 *   <li><b>Identity</b> and <b>content validation</b> have no safe default — the
 *       call fails with 503 rather than inventing an account or playing
 *       something that may not exist.</li>
 *   <li><b>Entitlements</b> cannot be guessed. Watching requires an active
 *       plan, so an unavailable payment-service answers 503 ("cannot verify
 *       the plan right now") instead of inventing either a free tier (which
 *       would let a lapsed account watch) or a denial (which would lock out a
 *       paying one).</li>
 *   <li><b>Progress</b> is best effort: losing one heartbeat is acceptable, the
 *       session keeps running and the next heartbeat catches up.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackDependencies {

    private final UserServiceClient userServiceClient;
    private final CatalogServiceClient catalogServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final CacheManager cacheManager;

    // ──────────────────────────── identity ────────────────────────────

    @Retry(name = "user-service")
    @CircuitBreaker(name = "user-service")
    @Bulkhead(name = "user-service")
    public UUID resolveUserId(Jwt jwt) {
        String subject = jwt.getSubject();
        UUID cached = readCache(subject);
        if (cached != null) {
            return cached;
        }
        UserServiceClient.UserAccount account = call("user-service", userServiceClient::currentUser);
        writeCache(subject, account.id());
        return account.id();
    }

    // ───────────────────────────── content ────────────────────────────

    @Retry(name = "catalog-service")
    @CircuitBreaker(name = "catalog-service")
    @Bulkhead(name = "catalog-service")
    public CatalogServiceClient.ContentSummary requireContent(UUID contentId) {
        try {
            return catalogServiceClient.currentContent(contentId);
        } catch (DownstreamServiceException ex) {
            if (ex.getDownstreamStatus() != null && ex.getDownstreamStatus().value() == 404) {
                throw new ResourceNotFoundException("Content", contentId);
            }
            throw ex;
        }
    }

    // ─────────────────────────── entitlements ─────────────────────────

    @Retry(name = "payment-service")
    @CircuitBreaker(name = "payment-service")
    @Bulkhead(name = "payment-service")
    public EntitlementsResult entitlements() {
        try {
            return EntitlementsResult.available(paymentServiceClient.currentEntitlements());
        } catch (RuntimeException ex) {
            log.warn("Entitlements unavailable: {}", ex.getMessage());
            return EntitlementsResult.unavailable();
        }
    }

    /**
     * "No plan" and "cannot check the plan" are different answers and the
     * caller must not confuse them: one is a paywall, the other is a retry.
     */
    public record EntitlementsResult(PaymentServiceClient.Entitlements entitlements, boolean degraded) {

        static EntitlementsResult available(PaymentServiceClient.Entitlements entitlements) {
            return new EntitlementsResult(entitlements, false);
        }

        static EntitlementsResult unavailable() {
            return new EntitlementsResult(null, true);
        }
    }

    // ──────────────────────────── progress ────────────────────────────

    /**
     * Best effort on purpose: a failed progress update must not fail the
     * heartbeat (the next one carries the same position anyway).
     */
    public void relayProgress(UUID profileId, UUID contentId, UUID episodeId,
                              int positionSeconds, Integer durationSeconds) {
        try {
            userServiceClient.recordProgress(profileId,
                new UserServiceClient.ProgressUpdate(contentId, episodeId, positionSeconds, durationSeconds, null));
        } catch (RuntimeException ex) {
            log.warn("Could not forward progress for profile {}: {}", profileId, ex.getMessage());
        }
    }

    // ───────────────────────────── helpers ────────────────────────────

    private <T> T call(String dependency, java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (DownstreamServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new DownstreamServiceException(dependency,
                "Could not reach %s: %s".formatted(dependency, ex.getMessage()));
        }
    }

    private UUID readCache(String subject) {
        Cache cache = cacheManager.getCache(CacheConfig.USER_ACCOUNT);
        return cache == null ? null : cache.get(subject, UUID.class);
    }

    private void writeCache(String subject, UUID userId) {
        Cache cache = cacheManager.getCache(CacheConfig.USER_ACCOUNT);
        if (cache != null) {
            cache.put(subject, userId);
        }
    }
}
