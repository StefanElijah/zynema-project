package dev.zynema.playback;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Base class for playback integration tests.
 *
 * <p>PostgreSQL, Redis and a real HTTP server standing in for the three
 * dependencies (user-service, catalog-service, payment-service). Pointing the
 * Feign clients at WireMock by URL exercises the whole client stack: shared
 * interceptor, timeouts, error decoder, circuit breaker and the explicit
 * degradation rules.
 */
@SpringBootTest
public abstract class AbstractPlaybackIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** Stands in for user-service, catalog-service and payment-service. */
    static final WireMockServer DEPENDENCIES = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        REDIS.start();
        DEPENDENCIES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        String stubUrl = "http://localhost:" + DEPENDENCIES.port();
        registry.add("spring.cloud.openfeign.client.config.user-service.url", () -> stubUrl);
        registry.add("spring.cloud.openfeign.client.config.catalog-service.url", () -> stubUrl);
        registry.add("spring.cloud.openfeign.client.config.payment-service.url", () -> stubUrl);
    }
}
