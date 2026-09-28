package dev.zynema.bff;

/**
 * The JSON the dependencies answer with, kept in one place so a contract change
 * breaks the stubs in a single file instead of every test.
 *
 * <p>Written by hand on purpose: these payloads are the wire contract between
 * the BFF and the services it consumes, and a generated copy of the producers'
 * classes would hide a rename instead of failing the tests.
 */
final class Stubs {

    private Stubs() {
    }

    static String catalogSummary(String id, String type, String title, String slug, int releaseYear) {
        return """
            {"id": "%s", "type": "%s", "title": "%s", "slug": "%s", "releaseYear": %d,
             "maturityRating": "16", "runtimeMinutes": 40, "posterUrl": "/%s.jpg",
             "backdropUrl": null, "averageRating": 8.5, "popularity": 90,
             "genres": [{"id": null, "name": "Drama", "slug": "drama"}]}
            """.formatted(id, type, title, slug, releaseYear, slug);
    }

    static String cataloguePage(String... summaries) {
        return """
            {"content": [%s], "page": 0, "size": 12, "totalElements": %d,
             "totalPages": 1, "first": true, "last": true}
            """.formatted(String.join(",", summaries), summaries.length);
    }

    static String catalogDetail(String id, String type, String title, String slug, String synopsis) {
        return """
            {"id": "%s", "type": "%s", "title": "%s", "originalTitle": null, "slug": "%s",
             "synopsis": "%s", "tagline": "Some tagline", "releaseYear": 2021,
             "maturityRating": "16", "runtimeMinutes": 40, "posterUrl": "/%s.jpg",
             "backdropUrl": null, "trailerUrl": null, "averageRating": 9.0, "popularity": 99,
             "genres": [{"id": null, "name": "Drama", "slug": "drama"}],
             "seasons": [{"id": null, "seasonNumber": 1, "title": "Season 1", "releaseYear": 2021, "episodeCount": 9}],
             "credits": [{"personName": "Hailee Steinfeld", "role": "ACTOR", "characterName": "Vi"}],
             "createdAt": "2021-11-06T00:00:00Z"}
            """.formatted(id, type, title, slug, synopsis, slug);
    }

    static String userAccount() {
        return """
            {"id": "%s", "email": "demo@zynema.dev", "displayName": "Demo User", "avatarUrl": null,
             "preferredLanguage": "es",
             "profiles": [{"id": "%s", "name": "Demo", "avatarKey": null, "kids": false, "language": "es"}]}
            """.formatted(AbstractBffIntegrationTest.DEMO_USER, AbstractBffIntegrationTest.DEMO_PROFILE);
    }

    static String continueWatching(String contentId) {
        return """
            [{"id": "73000000-0000-4000-8000-000000000001", "contentId": "%s", "episodeId": null,
              "positionSeconds": 600, "durationSeconds": 2400, "completed": false,
              "lastWatchedAt": "2026-09-01T00:00:00Z"}]
            """.formatted(contentId);
    }

    static String watchlist(String contentId) {
        return """
            [{"id": "74000000-0000-4000-8000-000000000001", "contentId": "%s",
              "addedAt": "2026-09-02T00:00:00Z"}]
            """.formatted(contentId);
    }

    static String subscription() {
        return """
            {"id": "81000000-0000-4000-8000-000000000002",
             "plan": {"code": "standard", "name": "Standard"},
             "status": "ACTIVE", "currentPeriodEnd": "2026-12-01T00:00:00Z",
             "cancelAtPeriodEnd": false}
            """;
    }

    static String entitlements(boolean active, int maxStreams) {
        return """
            {"active": %s, "maxStreams": %d, "maxQuality": "FHD", "planCode": "standard",
             "validUntil": "2026-12-01T00:00:00Z"}
            """.formatted(active, maxStreams);
    }
}
