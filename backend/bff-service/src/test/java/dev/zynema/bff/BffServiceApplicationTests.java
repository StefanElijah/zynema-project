package dev.zynema.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the reactive BFF context must boot with the shared reactive
 * auto-configuration from zynema-common (exception handler + correlation filter)
 * and without external dependencies (Eureka, Config Server, Redis, Keycloak).
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false"
})
class BffServiceApplicationTests {

    @Test
    void contextLoads() {
        // Fails if the WebFlux stack, springdoc, or common's reactive beans break.
    }
}
