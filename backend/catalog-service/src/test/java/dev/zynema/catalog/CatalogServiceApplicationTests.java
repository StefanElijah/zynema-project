package dev.zynema.catalog;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the catalog context must boot against a real PostgreSQL and a
 * real Redis, with Flyway applying V1–V3 and Hibernate validating every
 * entity against the resulting schema.
 */
class CatalogServiceApplicationTests extends AbstractCatalogIntegrationTest {

    @Test
    void contextLoads() {
        // Fails if bean wiring, YAML properties, Flyway migrations or JPA
        // mappings are broken.
    }
}
