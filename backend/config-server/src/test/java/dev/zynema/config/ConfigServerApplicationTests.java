package dev.zynema.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the config server must boot in "native" mode.
 * The native profile reads configs from the filesystem/classpath instead of a
 * Git backend, which is what local development and tests use.
 */
@SpringBootTest(properties = {
    "spring.cloud.config.server.native.search-locations=classpath:/config-repo/",
    "eureka.client.enabled=false"
})
@ActiveProfiles("native")
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {
        // Fails if the config server cannot start or resolve its backend.
    }
}
