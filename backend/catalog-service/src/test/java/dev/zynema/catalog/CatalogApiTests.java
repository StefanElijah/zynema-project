package dev.zynema.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
 * End-to-end HTTP tests: real Spring context, real PostgreSQL, real Redis,
 * MockMvc instead of a live servlet container. Covers the public read API,
 * request validation and the admin write API.
 */
@AutoConfigureMockMvc
@Transactional
class CatalogApiTests extends AbstractCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    /** Authenticated caller holding the content-manager realm role. */
    private static RequestPostProcessor asContentManager() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_content-manager"));
    }

    /** Authenticated caller with no management role. */
    private static RequestPostProcessor asPlainUser() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_user"));
    }

    // ────────────────────────────── reads ─────────────────────────────

    @Test
    @DisplayName("GET /movies returns a paginated catalog")
    void listMovies() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/movies").param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(28)))
            .andExpect(jsonPath("$.content", hasSize(5)))
            .andExpect(jsonPath("$.content[0].slug", is("dune-part-two")))
            .andExpect(jsonPath("$.content[0].genres", hasSize(4)))
            .andExpect(jsonPath("$.first", is(true)));
    }

    @Test
    @DisplayName("GET /movies filters by genre")
    void listMoviesByGenre() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/movies").param("genre", "horror").param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(3)))
            .andExpect(jsonPath("$.content[*].slug", containsInAnyOrder(
                "alien-romulus", "five-nights-at-freddys", "five-nights-at-freddys-2")));
    }

    @Test
    @DisplayName("GET /movies rejects an out-of-range page size")
    void listMoviesRejectsBadSize() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/movies").param("size", "500"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /movies/{slug} returns the detail view")
    void getMovieDetail() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/movies/dune-part-two"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", is("Dune: Part Two")))
            .andExpect(jsonPath("$.type", is("MOVIE")))
            .andExpect(jsonPath("$.seasons", hasSize(0)))
            .andExpect(jsonPath("$.credits", hasSize(4)));
    }

    @Test
    @DisplayName("GET /movies/{slug} returns 404 for a series slug")
    void getMovieDetailForSeriesIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/movies/arcane"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status", is(404)))
            .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("GET /series/{slug} returns seasons with episode counts")
    void getSeriesDetail() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/series/arcane"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", is("Arcane")))
            .andExpect(jsonPath("$.seasons", hasSize(2)))
            .andExpect(jsonPath("$.seasons[0].episodeCount", is(9)));
    }

    @Test
    @DisplayName("GET /series/{slug}/seasons/{n}/episodes returns the episode list")
    void listEpisodes() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/series/arcane/seasons/1/episodes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(9)))
            .andExpect(jsonPath("$[0].title", is("Welcome to the Playground")))
            .andExpect(jsonPath("$[8].title", is("The Monster You Created")));
    }

    @Test
    @DisplayName("GET /series/{slug}/seasons/{n}/episodes returns 422 for a movie")
    void listEpisodesForMovieIsUnprocessable() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/series/dune-part-two/seasons/1/episodes"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GET /search returns ranked matches")
    void search() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/search").param("q", "dune"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(2)))
            .andExpect(jsonPath("$.content[0].slug", is("dune-part-two")));
    }

    @Test
    @DisplayName("GET /search without q is a validation error")
    void searchWithoutQueryIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/search"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /genres returns the taxonomy")
    void listGenres() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/genres"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(15)))
            .andExpect(jsonPath("$[0].slug", is("action")));
    }

    @Test
    @DisplayName("unknown routes return 404")
    void unknownRoute() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/nope"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /contents/{id} resolves any title by id, anonymously")
    void getContentById() throws Exception {
        String id = idOf("arcane");

        mockMvc.perform(get("/api/v1/catalog/contents/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(id)))
            .andExpect(jsonPath("$.title", is("Arcane")))
            .andExpect(jsonPath("$.type", is("SERIES")))
            .andExpect(jsonPath("$.seasons", hasSize(2)));
    }

    @Test
    @DisplayName("GET /contents/{id} returns 404 for an unknown id")
    void unknownContentIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/contents/00000000-0000-4000-8000-000000000000"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("GET /contents/{id} with a malformed id is a 400")
    void malformedContentIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/contents/not-a-uuid"))
            .andExpect(status().isBadRequest());
    }

    // ────────────────────────────── writes ────────────────────────────

    @Test
    @DisplayName("POST admin creates an entry")
    void createContent() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/admin/contents")
                .with(asContentManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "MOVIE",
                      "title": "API Test Movie",
                      "slug": "api-test-movie",
                      "releaseYear": 2026,
                      "averageRating": 8.1,
                      "popularity": 5,
                      "genreSlugs": ["sci-fi"],
                      "metadata": {"origin": "test"}
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug", is("api-test-movie")))
            .andExpect(jsonPath("$.genres[0].slug", is("sci-fi")));
    }

    @Test
    @DisplayName("POST admin rejects an invalid payload with field violations")
    void createContentRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/admin/contents")
                .with(asContentManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "MOVIE",
                      "title": "",
                      "slug": "Not A Valid Slug",
                      "averageRating": 42.0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("Validation failed")))
            .andExpect(jsonPath("$.violations", hasSize(3)));
    }

    @Test
    @DisplayName("PUT admin updates an entry")
    void updateContent() throws Exception {
        String id = idOf("the-matrix");

        mockMvc.perform(put("/api/v1/catalog/admin/contents/" + id)
                .with(asContentManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "The Matrix (updated)",
                      "releaseYear": 1999,
                      "genreSlugs": ["sci-fi", "action"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title", is("The Matrix (updated)")))
            .andExpect(jsonPath("$.genres", hasSize(2)));
    }

    @Test
    @DisplayName("DELETE admin removes an entry")
    void deleteContent() throws Exception {
        String id = idOf("gladiator");

        mockMvc.perform(delete("/api/v1/catalog/admin/contents/" + id).with(asContentManager()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/catalog/movies/gladiator"))
            .andExpect(status().isNotFound());
    }

    // ─────────────────────── authorization matrix ──────────────────────

    @Test
    @DisplayName("anonymous callers cannot write, even to the admin API")
    void anonymousWritesAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/admin/contents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type": "MOVIE", "title": "Nope", "slug": "nope"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("an authenticated caller without the role gets 403, not 401")
    void authenticatedWithoutRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/admin/contents")
                .with(asPlainUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type": "MOVIE", "title": "Nope", "slug": "nope"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error", is("Forbidden")));
    }

    @Test
    @DisplayName("the admin role also grants content management")
    void adminRoleCanManageContent() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/admin/contents")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type": "MOVIE", "title": "By Admin", "slug": "by-admin", "genreSlugs": ["drama"]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug", is("by-admin")));
    }

    @Test
    @DisplayName("the public read API stays anonymous")
    void publicReadsStayAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/movies").param("size", "1"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());
    }

    /** Resolves a seeded title's id, whichever type it belongs to. */
    private String idOf(String slug) throws Exception {
        var result = mockMvc.perform(get("/api/v1/catalog/movies/" + slug)).andReturn();
        if (result.getResponse().getStatus() != 200) {
            result = mockMvc.perform(get("/api/v1/catalog/series/" + slug)).andReturn();
        }
        org.springframework.test.util.AssertionErrors.assertEquals(
            "expected a seeded title with slug " + slug, 200, result.getResponse().getStatus());
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
