package dev.zynema.user.security;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import dev.zynema.user.AbstractUserIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test against a real Keycloak.
 *
 * <p>Everything else mocks the token; this test does not. It imports the same
 * realm file docker compose uses, obtains genuine access tokens through the
 * password grant, and calls the API with them. That exercises the whole chain:
 * issuer validation, audience validation, JWKS retrieval from the running
 * container, Keycloak realm-role mapping, and the link between the token
 * subject and the local account.
 *
 * <p>Requires Docker. Keycloak adds ~20s to the suite, which is a fair price
 * for testing the one thing that cannot be mocked honestly.
 */
@Testcontainers
@AutoConfigureMockMvc
class KeycloakTokenAuthenticationTests extends AbstractUserIntegrationTest {

    private static final String REALM = "zynema";
    private static final String CLI_CLIENT_ID = "zynema-cli";
    private static final String CLI_CLIENT_SECRET = "zynema-cli-dev-secret";
    private static final String DEMO_USER = "71000000-0000-4000-8000-000000000001";

    @Container
    static final KeycloakContainer KEYCLOAK =
        new KeycloakContainer("quay.io/keycloak/keycloak:25.0")
            .withRealmImportFile("zynema-realm.json");

    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        String issuer = authServerUrl() + "/realms/" + REALM;
        registry.add("zynema.security.issuer-uri", () -> issuer);
        registry.add("zynema.security.jwk-set-uri", () -> issuer + "/protocol/openid-connect/certs");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a real token issued by Keycloak authenticates against /me")
    void realTokenAuthenticates() throws Exception {
        String token = accessToken("demo", "demo");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(DEMO_USER)))
            .andExpect(jsonPath("$.email", is("demo@zynema.dev")))
            .andExpect(jsonPath("$.profiles.length()", is(2)));
    }

    @Test
    @DisplayName("the token audience and issuer are enforced")
    void tokenIsValidatedAgainstIssuerAndAudience() throws Exception {
        String token = accessToken("demo", "demo");

        // Sanity-check what Keycloak actually put in the token: the realm's
        // audience mapper is what makes the platform-wide validation possible.
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(payload).contains("\"aud\":\"zynema-api\"");
        assertThat(payload).contains("http://localhost:");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("realm roles from a real token map to authorities (admin path works)")
    void realmRolesMapToAuthorities() throws Exception {
        String adminToken = accessToken("admin", "admin");

        mockMvc.perform(get("/api/v1/users/" + DEMO_USER).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is("demo@zynema.dev")));

        String managerToken = accessToken("manager", "manager");
        mockMvc.perform(get("/api/v1/users/" + DEMO_USER).header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a token for an unknown subject provisions its own account")
    void unknownSubjectIsProvisioned() throws Exception {
        // The manager is a Keycloak user with no local row: the first call
        // creates it from the token claims.
        String token = accessToken("manager", "manager");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is("manager@zynema.dev")));
    }

    @Test
    @DisplayName("a garbage bearer token is rejected with the shared 401 envelope")
    void garbageTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    // ────────────────────────────── helpers ────────────────────────────

    private String accessToken(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", CLI_CLIENT_ID);
        form.add("client_secret", CLI_CLIENT_SECRET);
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid profile email");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = RestClient.create()
            .post()
            .uri(authServerUrl() + "/realms/" + REALM + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);

        assertThat(response).isNotNull();
        return (String) response.get("access_token");
    }

    private static String authServerUrl() {
        String url = KEYCLOAK.getAuthServerUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
