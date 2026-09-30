package dev.zynema.bff;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Base for BFF integration tests.
 *
 * <p>Three <strong>real HTTP servers</strong> stand in for the dependencies and
 * a real Redis backs the cache. The clients are pointed at the servers by URL,
 * so the whole stack is exercised — load-balancer builder, token relay,
 * correlation filter, timeouts, circuit breakers — instead of a mocked
 * interface that would hide exactly the wiring these tests exist to prove.
 *
 * <p>The circuit breakers are reset before every test: the registry is shared
 * by the context, and a breaker left open by one test would silently change
 * what the next one measures.
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.config.enabled=false"
})
public abstract class AbstractBffIntegrationTest {

    static final WireMockServer CATALOG = new WireMockServer(options().dynamicPort());
    static final WireMockServer USER = new WireMockServer(options().dynamicPort());
    static final WireMockServer PAYMENT = new WireMockServer(options().dynamicPort());
    static final WireMockServer PLAYBACK = new WireMockServer(options().dynamicPort());

    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        CATALOG.start();
        USER.start();
        PAYMENT.start();
        PLAYBACK.start();
        REDIS.start();
    }

    static final String DEMO_SUBJECT = "11111111-1111-4111-8111-111111111111";
    static final String DEMO_USER = "71000000-0000-4000-8000-000000000001";
    static final String DEMO_PROFILE = "72000000-0000-4000-8000-000000000001";
    static final String ARCANE = "a2000000-0000-4000-8000-000000000002";
    static final String DUNE = "a1000000-0000-4000-8000-000000000003";
    static final String TEST_TOKEN = "test-token";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private CacheManager cacheManager;

    protected WebTestClient client;

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("zynema.bff.clients.catalog", () -> "http://localhost:" + CATALOG.port());
        registry.add("zynema.bff.clients.user", () -> "http://localhost:" + USER.port());
        registry.add("zynema.bff.clients.payment", () -> "http://localhost:" + PAYMENT.port());
        registry.add("zynema.bff.clients.playback", () -> "http://localhost:" + PLAYBACK.port());
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @BeforeEach
    void resetState() {
        client = WebTestClient.bindToApplicationContext(context)
            .apply(springSecurity())
            .build();
        CATALOG.resetAll();
        USER.resetAll();
        PAYMENT.resetAll();
        PLAYBACK.resetAll();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        cacheManager.getCacheNames().forEach(name ->
            java.util.Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    /**
     * An authenticated caller: the demo subject, the claims a Keycloak token
     * carries, and a token value the relay can forward.
     */
    protected SecurityMockServerConfigurers.JwtMutator asDemo() {
        return mockJwt().jwt(jwt -> jwt
                .tokenValue(TEST_TOKEN)
                .subject(DEMO_SUBJECT)
                .claim("preferred_username", "demo")
                .claim("email", "demo@zynema.dev")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("user"))))
            .authorities(new SimpleGrantedAuthority("ROLE_user"));
    }
}
