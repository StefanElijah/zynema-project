package dev.zynema.payment;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the context must boot against a real PostgreSQL with Flyway
 * applying V1–V2, Hibernate validating the model, the security chain wired and
 * the Feign client registered.
 */
class PaymentServiceApplicationTests extends AbstractPaymentIntegrationTest {

    @Test
    void contextLoads() {
        // Fails if bean wiring, YAML properties, Flyway migrations or JPA
        // mappings are broken.
    }
}
