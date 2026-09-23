package dev.zynema.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Eureka server context must boot.
 * Eureka client registration is already disabled in application.yml
 * (register-with-eureka: false), so this is a pure context-load test.
 */
@SpringBootTest
class EurekaServerApplicationTests {

    @Test
    void contextLoads() {
        // Fails if any bean wiring is broken.
    }
}
