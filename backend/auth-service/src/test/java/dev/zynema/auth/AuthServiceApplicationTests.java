package dev.zynema.auth;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the context must boot against a real PostgreSQL with Flyway
 * applying the baseline and the security chain auto-configured.
 */
class AuthServiceApplicationTests extends AbstractAuthIntegrationTest {

    @Test
    void contextLoads() {
        // Fails if bean wiring, YAML properties, Flyway migrations or the
        // security auto-configuration are broken.
    }
}
