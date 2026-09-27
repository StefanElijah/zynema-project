package dev.zynema.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the auto-configured default chain really applies: a service with no
 * security configuration of its own is <em>secure by default</em>, answers
 * 401 with the shared envelope, and still exposes the public operational
 * paths.
 */
// The fixtures are registered explicitly: Spring Boot's test TypeExcludeFilter
// keeps nested test classes out of component scanning, which would otherwise
// leave the controllers unmapped (and every request a 404).
@SpringBootTest(classes = {
    DefaultSecurityChainTest.TestApplication.class,
    DefaultSecurityChainTest.PingController.class
})
@AutoConfigureMockMvc
class DefaultSecurityChainTest {

    private final MockMvc mockMvc;

    @Autowired
    DefaultSecurityChainTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("an anonymous request to a protected endpoint gets a 401 ApiError, not a redirect")
    void anonymousRequestIsRejectedWithApiError() throws Exception {
        mockMvc.perform(get("/test/ping"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.path").value("/test/ping"))
            .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("an authenticated request is allowed through")
    void authenticatedRequestIsAllowed() throws Exception {
        mockMvc.perform(get("/test/ping").with(jwt().jwt(jwt -> jwt.subject("someone"))))
            .andExpect(status().isOk())
            .andExpect(content().string("pong"));
    }

    @Test
    @DisplayName("operational endpoints stay public")
    void operationalEndpointsArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("UP"));
    }

    @SpringBootApplication
    static class TestApplication {
    }

    @RestController
    static class PingController {
        @GetMapping("/test/ping")
        String ping() {
            return "pong";
        }

        /** Stand-in for the actuator endpoint, which is not on this module's test classpath. */
        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }
    }
}
