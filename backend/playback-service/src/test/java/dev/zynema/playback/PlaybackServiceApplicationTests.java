package dev.zynema.playback;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the context must boot with Flyway applying V1, Hibernate
 * validating the model, the security chain wired and the three Feign clients
 * registered.
 */
class PlaybackServiceApplicationTests extends AbstractPlaybackIntegrationTest {

    @Test
    void contextLoads() {
        // Fails if bean wiring, YAML properties, Flyway migrations or JPA
        // mappings are broken.
    }
}
