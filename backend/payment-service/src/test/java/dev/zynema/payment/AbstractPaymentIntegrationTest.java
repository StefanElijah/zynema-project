package dev.zynema.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Base class for payment integration tests.
 *
 * <p>Runs against a real PostgreSQL, a real Redis and a <strong>real HTTP
 * server</strong> standing in for user-service. The Feign client is pointed at
 * WireMock by URL, so the whole client stack — interceptor, timeouts, error
 * decoder, circuit breaker — is exercised instead of a mocked interface.
 *
 * <p>Containers are started once per JVM (singleton pattern) and reaped by
 * Testcontainers at exit.
 */
@SpringBootTest
public abstract class AbstractPaymentIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** Stands in for user-service. */
    static final WireMockServer USER_SERVICE = new WireMockServer(options().dynamicPort());

    static {
        POSTGRES.start();
        REDIS.start();
        USER_SERVICE.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // An explicit URL makes Feign skip service discovery for this client.
        registry.add("spring.cloud.openfeign.client.config.user-service.url",
            () -> "http://localhost:" + USER_SERVICE.port());
    }
}
