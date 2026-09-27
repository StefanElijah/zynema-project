package dev.zynema.payment.service;

import dev.zynema.common.exception.DownstreamServiceException;
import dev.zynema.payment.client.UserAccountDto;
import dev.zynema.payment.client.UserServiceClient;
import dev.zynema.payment.config.CacheConfig;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
 * Resolves the local account id of the authenticated caller.
 *
 * <p>The token carries the Keycloak subject, but billing data references the
 * account that user-service owns (ADR-0019). The first authenticated request
 * pays for a Feign round trip; the mapping is then cached, so the cost is paid
 * once per session rather than on every call.
 *
 * <p>Resilience: the circuit breaker and the time limiter come from the Feign
 * integration (see {@code resilience4j.*} in application.yml), retries are
 * limited to transient transport failures, and the bulkhead caps how many
 * concurrent requests can be waiting on user-service. When the dependency is
 * unavailable there is no safe default — an invented account id would attach
 * billing data to the wrong person — so the call fails with 503.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentAccountService {

    private final UserServiceClient userServiceClient;
    private final CacheManager cacheManager;

    /**
     * Composition, from outside in: {@code @Retry( @CircuitBreaker( @Bulkhead( call ) ) )},
     * which is Resilience4j's aspect order.
     *
     * <p>The circuit breaker is declared here rather than through the Feign
     * integration on purpose: that integration names the instance after the
     * generated client type and method ({@code UserServiceClientcurrentUser}),
     * so renaming a method would silently detach the tuning. An explicit name
     * keeps {@code resilience4j.*.instances.user-service} meaningful, and the
     * HTTP read timeout already bounds a hung call.
     */
    @Retry(name = "user-service")
    @CircuitBreaker(name = "user-service")
    @Bulkhead(name = "user-service")
    public UUID resolveUserId(Jwt jwt) {
        String subject = jwt.getSubject();
        UUID cached = readFromCache(subject);
        if (cached != null) {
            return cached;
        }
        UserAccountDto account = fetchAccount();
        writeToCache(subject, account.id());
        return account.id();
    }

    private UserAccountDto fetchAccount() {
        try {
            return userServiceClient.currentUser();
        } catch (DownstreamServiceException ex) {
            throw ex;
        } catch (CallNotPermittedException ex) {
            throw new DownstreamServiceException("user-service",
                "user-service is temporarily unavailable (circuit open)");
        } catch (RuntimeException ex) {
            log.warn("Could not resolve the account from user-service", ex);
            throw new DownstreamServiceException("user-service",
                "Could not resolve the account: " + ex.getMessage());
        }
    }

    private UUID readFromCache(String subject) {
        Cache cache = cacheManager.getCache(CacheConfig.USER_ACCOUNT);
        return cache == null ? null : cache.get(subject, UUID.class);
    }

    private void writeToCache(String subject, UUID userId) {
        Cache cache = cacheManager.getCache(CacheConfig.USER_ACCOUNT);
        if (cache != null) {
            cache.put(subject, userId);
        }
    }
}
