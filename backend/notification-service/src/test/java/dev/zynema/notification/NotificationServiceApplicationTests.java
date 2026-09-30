package dev.zynema.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: the service context must boot against a real PostgreSQL,
 * running Flyway migrations and validating the JPA model.
 *
 * This is the regression guard for the malformed-YAML class of bugs found
 * in Fase 1 (application.yml blocks concatenated without newlines), which
 * plain compilation never catches.
 */
@Testcontainers
@SpringBootTest
class NotificationServiceApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Fails if any bean wiring, YAML property, Flyway migration or
        // JPA mapping is broken.
    }
}
