package dev.zynema.user;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the context must boot against a real PostgreSQL with Flyway
 * applying V1–V2 and Hibernate validating the entity model.
 */
class UserServiceApplicationTests extends AbstractUserIntegrationTest {

    @Test
    void contextLoads() {
        // Fails if bean wiring, YAML properties, Flyway migrations or JPA
        // mappings are broken.
    }
}
