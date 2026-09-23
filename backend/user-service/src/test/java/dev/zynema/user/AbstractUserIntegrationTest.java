package dev.zynema.user;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for user-service integration tests.
 *
 * <p>Singleton-container pattern: one PostgreSQL container per test JVM,
 * shared by every subclass and reaped by Testcontainers' Ryuk at JVM exit.
 * Redis is not needed: Phase 2 does not cache in this service.
 */
@SpringBootTest
public abstract class AbstractUserIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("management.health.redis.enabled", () -> "false");
    }
}
