package dev.zynema.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP tests for user-service.
 *
 * <p>Self-service is exercised through {@code /api/v1/users/me/**}: the caller
 * is identified by the token, so the tests sign tokens for specific subjects
 * (the seeded demo has a deterministic subject thanks to the realm export).
 *
 * <p>Id-addressed endpoints are admin-only and tested as such.
 */
@AutoConfigureMockMvc
@Transactional
class UserApiTests extends AbstractUserIntegrationTest {

    private static final String DEMO_SUBJECT = "11111111-1111-4111-8111-111111111111";
    private static final String DEMO_USER = "71000000-0000-4000-8000-000000000001";
    private static final String DEMO_PROFILE = "72000000-0000-4000-8000-000000000001";
    private static final String KIDS_PROFILE = "72000000-0000-4000-8000-000000000002";
    private static final String DUNE_TWO = "a1000000-0000-4000-8000-000000000003";

    @Autowired
    private MockMvc mockMvc;

    /** The seeded account, as Keycloak would present it. */
    private static RequestPostProcessor asDemo() {
        return jwt().jwt(jwt -> jwt
            .subject(DEMO_SUBJECT)
            .claim("email", "demo@zynema.dev")
            .claim("email_verified", true)
            .claim("preferred_username", "demo")
            .claim("realm_access", java.util.Map.of("roles", java.util.List.of("user"))))
            .authorities(new SimpleGrantedAuthority("ROLE_user"));
    }

    private static RequestPostProcessor asAdmin() {
        return jwt().jwt(jwt -> jwt.subject("admin-subject"))
            .authorities(new SimpleGrantedAuthority("ROLE_admin"));
    }

    // ──────────────────────────── self-service ─────────────────────────

    @Test
    @DisplayName("GET /me resolves the account from the token subject")
    void meReturnsAccount() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(DEMO_USER)))
            .andExpect(jsonPath("$.email", is("demo@zynema.dev")))
            .andExpect(jsonPath("$.profiles", hasSize(2)));
    }

    @Test
    @DisplayName("GET /me provisions a local account on the first call")
    void meProvisionsUnknownSubject() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(jwt().jwt(jwt -> jwt
                .subject("brand-new-subject")
                .claim("email", "newcomer@zynema.dev")
                .claim("email_verified", true)
                .claim("name", "New Comer"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is("newcomer@zynema.dev")))
            .andExpect(jsonPath("$.displayName", is("New Comer")))
            .andExpect(jsonPath("$.profiles", hasSize(0)));
    }

    @Test
    @DisplayName("GET /me without a token is a 401 with the shared envelope")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("two different subjects never see each other's account")
    void differentSubjectsGetDifferentAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(jwt().jwt(jwt -> jwt
                .subject("someone-else").claim("email", "other@zynema.dev").claim("email_verified", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is("other@zynema.dev")))
            .andExpect(jsonPath("$.id", is(org.hamcrest.Matchers.not(DEMO_USER))));
    }

    // ────────────────────────────── profiles ───────────────────────────

    @Test
    @DisplayName("POST /me/profiles creates a profile for the caller")
    void createProfile() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/profiles").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Tablet", "avatarKey": "avatar-05", "kids": false}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is("Tablet")));

        mockMvc.perform(get("/api/v1/users/me/profiles").with(asDemo()))
            .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("DELETE /me/profiles/{id} removes one of my profiles")
    void deleteProfile() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/profiles/" + KIDS_PROFILE).with(asDemo()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/profiles").with(asDemo()))
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("a profile that belongs to another account is invisible (404)")
    void foreignProfileIsNotVisible() throws Exception {
        // A different subject asks for the demo profile: the profile does not
        // belong to their account, so it is reported as missing.
        mockMvc.perform(get("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist")
                .with(jwt().jwt(jwt -> jwt.subject("intruder").claim("email", "intruder@zynema.dev"))))
            .andExpect(status().isNotFound());
    }

    // ───────────────────────────── watchlist ───────────────────────────

    @Test
    @DisplayName("GET /me/profiles/{id}/watchlist returns my list")
    void listWatchlist() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("adding the same title twice keeps a single entry")
    void addToWatchlistIsIdempotent() throws Exception {
        String body = """
            {"contentId": "a1000000-0000-4000-8000-000000000027"}
            """;

        mockMvc.perform(post("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist")
                .with(asDemo()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist")
                .with(asDemo()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist").with(asDemo()))
            .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    @DisplayName("DELETE removes the title from my list")
    void removeFromWatchlist() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist/" + DUNE_TWO)
                .with(asDemo()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/watchlist").with(asDemo()))
            .andExpect(jsonPath("$", hasSize(2)));
    }

    // ───────────────────────────── progress ────────────────────────────

    @Test
    @DisplayName("PUT progress upserts playback progress")
    void recordProgress() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/progress").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"contentId": "%s", "positionSeconds": 600, "durationSeconds": 9960, "completed": false}
                    """.formatted(DUNE_TWO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.positionSeconds", is(600)))
            .andExpect(jsonPath("$.completed", is(false)));
    }

    @Test
    @DisplayName("continue-watching returns in-progress titles only")
    void continueWatching() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/continue-watching").with(asDemo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("a negative position is a validation error")
    void progressValidation() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/profiles/" + DEMO_PROFILE + "/progress").with(asDemo())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"contentId": "%s", "positionSeconds": -5}
                    """.formatted(DUNE_TWO)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("Validation failed")));
    }

    // ─────────────────────────────── admin ─────────────────────────────

    @Test
    @DisplayName("the admin API is closed to normal users")
    void adminApiRejectsNormalUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + DEMO_USER).with(asDemo()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an admin can address accounts by id")
    void adminCanAddressAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + DEMO_USER).with(asAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is("demo@zynema.dev")));

        mockMvc.perform(get("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/watchlist")
                .with(asAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("the admin API still requires a token")
    void adminApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + DEMO_USER))
            .andExpect(status().isUnauthorized());
    }
}
