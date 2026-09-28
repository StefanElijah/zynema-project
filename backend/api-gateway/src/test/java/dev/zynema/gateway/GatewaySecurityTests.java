package dev.zynema.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * Route authorization at the edge.
 *
 * <p>No downstream service runs here, so an <em>authorized</em> request is
 * expected to fail at routing (5xx: no load-balanced instance to forward to).
 * That is exactly the signal this test needs: a 5xx means the request passed
 * the security filters and reached the routing layer, while 401/403 means it
 * was stopped at the edge.
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.gateway.server.webflux.discovery.locator.enabled=false"
})
@TestPropertySource(properties = "spring.main.web-application-type=reactive")
class GatewaySecurityTests {

    @Autowired
    private ApplicationContext context;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context)
            .apply(springSecurity())
            .build();
    }

    // ───────────────────────────── public ──────────────────────────────

    @Test
    @DisplayName("anyone can browse the catalogue")
    void catalogueReadsArePublic() {
        client.get().uri("/api/v1/catalog/movies")
            .exchange()
            .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("the auth public endpoints are reachable before login")
    void authPublicEndpointsArePublic() {
        client.get().uri("/api/v1/auth/public/config")
            .exchange()
            .expectStatus().is5xxServerError();
    }

    // ──────────────────────────── protected ────────────────────────────

    @Test
    @DisplayName("anonymous writes to the admin API stop at the edge with a 401 envelope")
    void adminWritesRequireAuthentication() {
        client.post().uri("/api/v1/catalog/admin/contents")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.error").isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("an authenticated user without the role gets 403")
    void adminWritesRequireTheRole() {
        client.mutateWith(jwtWithRole("user"))
            .post().uri("/api/v1/catalog/admin/contents")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.error").isEqualTo("Forbidden");
    }

    @Test
    @DisplayName("content-manager may reach the admin API")
    void contentManagerPassesTheEdge() {
        client.mutateWith(jwtWithRole("content-manager"))
            .post().uri("/api/v1/catalog/admin/contents")
            .exchange()
            .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("self-service endpoints need a token but no special role")
    void userSelfServiceNeedsAuthenticationOnly() {
        client.get().uri("/api/v1/users/me")
            .exchange()
            .expectStatus().isUnauthorized();

        client.mutateWith(jwtWithRole("user"))
            .get().uri("/api/v1/users/me")
            .exchange()
            .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("the BFF landing page and content metadata are reachable anonymously")
    void bffReadsArePublic() {
        client.get().uri("/api/v1/web/home")
            .exchange()
            .expectStatus().is5xxServerError();

        client.get().uri("/api/v1/web/catalog/arcane")
            .exchange()
            .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("the account view and the profile rails are not reachable anonymously")
    void bffPersonalViewsRequireAuthentication() {
        client.get().uri("/api/v1/web/account")
            .exchange()
            .expectStatus().isUnauthorized();

        client.get().uri("/api/v1/web/profiles/72000000-0000-4000-8000-000000000001/home")
            .exchange()
            .expectStatus().isUnauthorized();

        client.mutateWith(jwtWithRole("user"))
            .get().uri("/api/v1/web/account")
            .exchange()
            .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("operational endpoints stay public")
    void healthIsPublic() {
        client.get().uri("/actuator/health")
            .exchange()
            .expectStatus().value(status -> {
                // Public, but the reactive actuator may not be reachable in this
                // slice; what matters is that security did not block it.
                if (status == 401 || status == 403) {
                    throw new AssertionError("health should not require authentication, got " + status);
                }
            });
    }

    private SecurityMockServerConfigurers.JwtMutator jwtWithRole(String role) {
        return mockJwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
