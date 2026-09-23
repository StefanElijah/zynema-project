package dev.zynema.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class UserApiTests extends AbstractUserIntegrationTest {

    private static final String DEMO_USER = "71000000-0000-4000-8000-000000000001";
    private static final String DEMO_PROFILE = "72000000-0000-4000-8000-000000000001";
    private static final String DUNE_TWO = "a1000000-0000-4000-8000-000000000003";

    @Autowired
    private MockMvc mockMvc;

    // ────────────────────────────── users ─────────────────────────────

    @Test
    @DisplayName("GET /users/{id} returns the account with profiles")
    void getUser() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + DEMO_USER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email", is("demo@zynema.dev")))
            .andExpect(jsonPath("$.profiles", hasSize(2)));
    }

    @Test
    @DisplayName("GET /users?email= returns the account")
    void getUserByEmail() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("email", "demo@zynema.dev"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(DEMO_USER)));
    }

    @Test
    @DisplayName("GET /users/{id} returns 404 for an unknown account")
    void unknownUserIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/00000000-0000-4000-8000-000000000000"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    @DisplayName("POST /users creates an account and validates the email")
    void createUser() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "api@zynema.dev", "displayName": "API User"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email", is("api@zynema.dev")));

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "not-an-email"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.violations[0].field", is("email")));
    }

    // ──────────────────────────── profiles ────────────────────────────

    @Test
    @DisplayName("POST /users/{id}/profiles creates a profile")
    void createProfile() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + DEMO_USER + "/profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Tablet", "avatarKey": "avatar-05", "kids": false}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is("Tablet")));

        mockMvc.perform(get("/api/v1/users/" + DEMO_USER + "/profiles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("DELETE /users/{id}/profiles/{profileId} removes the profile")
    void deleteProfile() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + DEMO_USER + "/profiles/72000000-0000-4000-8000-000000000002"))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/" + DEMO_USER + "/profiles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    // ──────────────────────────── activity ────────────────────────────

    @Test
    @DisplayName("GET watchlist returns the seeded entries")
    void listWatchlist() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/watchlist"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("POST watchlist adds a title idempotently")
    void addToWatchlist() throws Exception {
        String body = """
            {"contentId": "a1000000-0000-4000-8000-000000000027"}
            """;

        mockMvc.perform(post("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/watchlist")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/watchlist")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/watchlist"))
            .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    @DisplayName("DELETE watchlist/{contentId} removes the title")
    void removeFromWatchlist() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/watchlist/" + DUNE_TWO))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/watchlist"))
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("PUT progress upserts playback progress")
    void recordProgress() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"contentId": "%s", "positionSeconds": 600, "durationSeconds": 9960, "completed": false}
                    """.formatted(DUNE_TWO)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.positionSeconds", is(600)))
            .andExpect(jsonPath("$.completed", is(false)));
    }

    @Test
    @DisplayName("GET continue-watching returns in-progress titles only")
    void continueWatching() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/continue-watching"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("progress with a negative position is rejected")
    void progressValidation() throws Exception {
        mockMvc.perform(put("/api/v1/users/" + DEMO_USER + "/profiles/" + DEMO_PROFILE + "/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"contentId": "%s", "positionSeconds": -5}
                    """.formatted(DUNE_TWO)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("Validation failed")));
    }

    @Test
    @DisplayName("activity through a foreign account is a 404")
    void foreignAccountIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/00000000-0000-4000-8000-000000000000/profiles/" + DEMO_PROFILE + "/watchlist"))
            .andExpect(status().isNotFound());
    }
}
