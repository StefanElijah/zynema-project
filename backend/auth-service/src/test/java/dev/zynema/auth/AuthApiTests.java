package dev.zynema.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthApiTests extends AbstractAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /me describes the session from the token claims")
    void meDescribesTheSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").with(jwt().jwt(jwt -> jwt
                .subject("11111111-1111-4111-8111-111111111111")
                .issuer("http://localhost:8180/realms/zynema")
                .audience(List.of("zynema-api"))
                .claim("preferred_username", "demo")
                .claim("email", "demo@zynema.dev")
                .claim("email_verified", true)
                .claim("name", "Demo User")
                .claim("realm_access", Map.of("roles", List.of("user", "content-manager")))
                .expiresAt(java.time.Instant.now().plusSeconds(600)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subject", is("11111111-1111-4111-8111-111111111111")))
            .andExpect(jsonPath("$.username", is("demo")))
            .andExpect(jsonPath("$.email", is("demo@zynema.dev")))
            .andExpect(jsonPath("$.emailVerified", is(true)))
            .andExpect(jsonPath("$.fullName", is("Demo User")))
            .andExpect(jsonPath("$.roles", containsInAnyOrder("user", "content-manager")))
            .andExpect(jsonPath("$.authorities", containsInAnyOrder("ROLE_user", "ROLE_content-manager")))
            .andExpect(jsonPath("$.issuer", is("http://localhost:8180/realms/zynema")))
            .andExpect(jsonPath("$.audience[0]", is("zynema-api")));
    }

    @Test
    @DisplayName("GET /me derives the name from given/family when `name` is absent")
    void meFallsBackToGivenAndFamilyName() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").with(jwt().jwt(jwt -> jwt
                .subject("someone")
                .claim("given_name", "Ada")
                .claim("family_name", "Lovelace"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fullName", is("Ada Lovelace")));
    }

    @Test
    @DisplayName("GET /me is not reachable anonymously")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("Unauthorized")))
            .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("the public config endpoint exposes no secrets and needs no token")
    void publicConfigIsAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/auth/public/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issuer", is("http://localhost:8180/realms/zynema")))
            .andExpect(jsonPath("$.realm", is("zynema")))
            .andExpect(jsonPath("$.clientId", is("zynema-web")))
            .andExpect(jsonPath("$.audience", is("zynema-api")))
            .andExpect(jsonPath("$.scopes", hasSize(3)));
    }
}
