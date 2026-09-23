package dev.zynema.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: the reactive gateway context must boot without a live Eureka
 * or Config Server. Discovery and config import are disabled for the test.
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.gateway.discovery.locator.enabled=false"
})
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Fails if route definitions or security config are broken.
    }
}
