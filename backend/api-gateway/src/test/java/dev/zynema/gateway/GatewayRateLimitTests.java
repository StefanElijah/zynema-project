package dev.zynema.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Edge rate limiting.
 *
 * <p>Runs against a real Redis: the limiter is an atomic Lua token bucket and
 * mocking it would test nothing.
 *
 * <p>Requests are issued <strong>concurrently</strong> on purpose. A token
 * bucket refills with time, so a sequential test is a race against how fast the
 * test machine answers: N requests fired at once all observe the same instant
 * and the atomic script decides exactly how many fit in the burst.
 *
 * <p>Authorized requests fail at routing here (no downstream instance), so a
 * 5xx means "passed security and rate limiting" and 429 means "rejected".
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.gateway.server.webflux.discovery.locator.enabled=false",
    "zynema.rate-limit.routes.catalog.replenish-rate=1",
    "zynema.rate-limit.routes.catalog.burst-capacity=2",
    "zynema.rate-limit.routes.catalog.requested-tokens=1",
    "zynema.rate-limit.routes.auth-public.replenish-rate=1",
    "zynema.rate-limit.routes.auth-public.burst-capacity=2"
})
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
class GatewayRateLimitTests {

    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private ApplicationContext context;

    private WebTestClient client;

    @BeforeEach
    void setUp() throws Exception {
        // Every test starts with an empty token bucket.
        REDIS.execInContainer("redis-cli", "FLUSHALL");
        client = WebTestClient.bindToApplicationContext(context)
            .apply(springSecurity())
            .build();
    }

    @Test
    @DisplayName("requests over the burst capacity get a 429 with the ApiError envelope")
    void exceedingTheBurstIsRejected() {
        List<Integer> statuses = fireConcurrently(10, "/api/v1/catalog/movies");

        // Burst is 2: most of the ten must be rejected even if a token or two
        // replenishes while the threads are being scheduled.
        assertThat(statuses).filteredOn(status -> status == 429).hasSizeGreaterThanOrEqualTo(5);
        assertThat(statuses).anySatisfy(status -> assertThat(status).isBetween(500, 599));
    }

    @Test
    @DisplayName("a rejected request explains itself: envelope, route and Retry-After")
    void rejectionCarriesTheContract() {
        fireConcurrently(10, "/api/v1/catalog/movies");

        client.get().uri("/api/v1/catalog/movies")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().exists("Retry-After")
            .expectBody()
            .jsonPath("$.status").isEqualTo(429)
            .jsonPath("$.error").isEqualTo("Too Many Requests")
            .jsonPath("$.details.route").isEqualTo("catalog")
            .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    @DisplayName("the limit is per caller: a signed-in viewer has their own bucket")
    void limitsArePerCaller() {
        fireConcurrently(10, "/api/v1/catalog/movies");

        client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_user")))
            .get().uri("/api/v1/catalog/movies")
            .exchange()
            .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("routes without a configured limit are never throttled")
    void unlimitedRoutesAreNotAffected() {
        List<Integer> statuses = Flux.range(0, 5)
            .flatMap(i -> Mono.fromCallable(() -> client
                    .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_user")))
                    .get().uri("/api/v1/notifications/ping")
                    .exchange()
                    .returnResult(Void.class)
                    .getStatus()
                    .value())
                .subscribeOn(Schedulers.boundedElastic()))
            .collectList()
            .block();

        assertThat(statuses).isNotNull().allSatisfy(status -> assertThat(status).isNotEqualTo(429));
    }

    @Test
    @DisplayName("a limited route advertises its bucket, an unlimited one does not")
    void limiterHeadersExposeTheEffectiveConfig() {
        var limited = client.get().uri("/api/v1/auth/public/config")
            .exchange()
            .returnResult(Void.class)
            .getResponseHeaders();

        assertThat(limited.getFirst("X-RateLimit-Burst-Capacity")).isEqualTo("2");
        assertThat(limited.getFirst("X-RateLimit-Replenish-Rate")).isEqualTo("1");

        var unlimited = client.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_user")))
            .get().uri("/api/v1/notifications/ping")
            .exchange()
            .returnResult(Void.class)
            .getResponseHeaders();

        assertThat(unlimited.getFirst("X-RateLimit-Burst-Capacity")).isNull();
    }

    /**
     * Fires the requests on parallel threads so they observe the same bucket
     * state. WebTestClient's exchange is blocking, so the calls are dispatched
     * on the elastic scheduler instead of the event loop.
     */
    private List<Integer> fireConcurrently(int requests, String path) {
        List<Integer> statuses = Flux.range(0, requests)
            .flatMap(i -> Mono.fromCallable(() -> client.get().uri(path)
                    .exchange()
                    .returnResult(Void.class)
                    .getStatus()
                    .value())
                .subscribeOn(Schedulers.boundedElastic()))
            .collectList()
            .block();
        assertThat(statuses).isNotNull().hasSize(requests);
        return statuses;
    }
}
