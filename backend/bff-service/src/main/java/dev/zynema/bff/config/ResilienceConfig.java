package dev.zynema.bff.config;

import dev.zynema.common.exception.ResourceNotFoundException;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.function.Predicate;

/**
 * What counts as a failure, expressed once for every downstream.
 *
 * <p>The threshold values live in {@code application.yml}, like in the domain
 * services. What cannot be expressed there is the <em>classification</em> of a
 * reactive HTTP outcome, and getting it wrong is expensive in both directions:
 * <ul>
 *   <li>A <strong>404 is not a failure</strong>. "This account has no
 *       subscription" and "that slug does not exist" are answers. Counting them
 *       would let a visitor with no plan, or a typo, open the circuit for
 *       everybody.</li>
 *   <li>A <strong>transport error or a 5xx is transient</strong> — worth
 *       retrying. A 4xx is not: repeating a rejected request changes nothing.
 *       A refused connection is worth retrying, but an open circuit is not
 *       retried at all, which is why {@code CallNotPermittedException} is
 *       ignored by the retry policy.</li>
 * </ul>
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerConfigCustomizer catalogCircuitBreakerConfig() {
        return circuitBreaker("catalog-service");
    }

    @Bean
    public CircuitBreakerConfigCustomizer userCircuitBreakerConfig() {
        return circuitBreaker("user-service");
    }

    @Bean
    public CircuitBreakerConfigCustomizer paymentCircuitBreakerConfig() {
        return circuitBreaker("payment-service");
    }

    @Bean
    public RetryConfigCustomizer catalogRetryConfig() {
        return retry("catalog-service");
    }

    @Bean
    public RetryConfigCustomizer userRetryConfig() {
        return retry("user-service");
    }

    @Bean
    public RetryConfigCustomizer paymentRetryConfig() {
        return retry("payment-service");
    }

    /**
     * Typed variables, not method references at the call site: the customizer
     * hands the builder of a raw generic, and the reference would be inferred
     * against {@code Object}.
     */
    private static final Predicate<Throwable> NOT_A_FAILURE = ResilienceConfig::isNotAFailure;
    private static final Predicate<Throwable> TRANSIENT = ResilienceConfig::isTransient;

    private static CircuitBreakerConfigCustomizer circuitBreaker(String name) {
        return CircuitBreakerConfigCustomizer.of(name, builder -> builder
            .ignoreException(NOT_A_FAILURE));
    }

    /**
     * Retry only the transient outcomes. Everything else — 4xx, an open
     * circuit — is not retried because {@code retryOnException} says so, which
     * also makes a separate ignore-list unnecessary.
     */
    private static RetryConfigCustomizer retry(String name) {
        return RetryConfigCustomizer.of(name, builder -> builder
            .retryOnException(TRANSIENT));
    }

    static boolean isNotAFailure(Throwable failure) {
        if (failure instanceof ResourceNotFoundException) {
            return true;
        }
        return failure instanceof WebClientResponseException response
            && response.getStatusCode().is4xxClientError();
    }

    static boolean isTransient(Throwable failure) {
        if (failure instanceof WebClientRequestException) {
            return true;
        }
        return failure instanceof WebClientResponseException response
            && response.getStatusCode().is5xxServerError();
    }
}
