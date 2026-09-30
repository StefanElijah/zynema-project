package dev.zynema.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Per-route rate limits, declared in configuration so a limit can be tuned
 * without touching code.
 *
 * <p>Values are token-bucket parameters: {@code replenishRate} is the sustained
 * requests per second and {@code burstCapacity} how many can be absorbed at
 * once. Routes absent from the map are not limited.
 *
 * @param routes route id → limit
 */
@ConfigurationProperties(prefix = "zynema.rate-limit")
public record RateLimitProperties(Map<String, RouteLimit> routes) {

    public record RouteLimit(int replenishRate, int burstCapacity, Integer requestedTokens) {

        public int requestedTokensOrDefault() {
            return requestedTokens == null ? 1 : requestedTokens;
        }
    }
}
