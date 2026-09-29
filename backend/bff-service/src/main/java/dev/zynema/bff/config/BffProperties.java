package dev.zynema.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where the BFF finds its dependencies and how long it waits for them.
 *
 * <p>The URLs are {@code lb://} URIs resolved through Eureka in every real
 * environment; tests point them at a plain HTTP server instead, which is the
 * only reason they are configurable.
 */
@ConfigurationProperties(prefix = "zynema.bff")
public record BffProperties(Clients clients, Http http, Cache cache) {

    public record Clients(String catalog, String user, String payment, String playback) {
    }

    /**
     * Aggregated screens are cached whole, with a TTL instead of eviction:
     * writes happen in the domain services, which know nothing about their
     * consumers, so staleness is bounded by time — short enough for a
     * personalized screen, longer for the anonymous landing page.
     */
    public record Cache(
        String keyPrefix,
        Duration homeTtl,
        Duration contentTtl,
        Duration accountTtl,
        Duration profileHomeTtl
    ) {
    }

    /**
     * @param connectTimeout  bound for opening the TCP connection
     * @param responseTimeout bound for the whole response: the BFF composes
     *                        several calls, so one slow dependency must not
     *                        hold the screen hostage
     */
    public record Http(Duration connectTimeout, Duration responseTimeout) {
    }
}
