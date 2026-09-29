package dev.zynema.videoworker.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.videoworker.config.WorkerProperties;
import dev.zynema.videoworker.pipeline.PipelineTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Talks to catalog-service on behalf of the pipeline.
 *
 * <p>Two different identities, on purpose:
 * <ul>
 *   <li><strong>Reads are anonymous.</strong> Resolving a slug to an id uses the
 *       public catalogue, exactly like any client.</li>
 *   <li><strong>The write is a machine identity.</strong> Setting an
 *       {@code hls_path} is an admin operation, so the worker authenticates
 *       with OAuth2 client credentials ({@code zynema-media}) and carries a
 *       {@code content-manager} role. No human token, no shared secret in a
 *       header.</li>
 * </ul>
 */
@Slf4j
@Component
public class CatalogAdminClient {

    private final WorkerProperties.Catalog settings;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newHttpClient();

    private String cachedToken;

    public CatalogAdminClient(WorkerProperties properties, ObjectMapper objectMapper) {
        this.settings = properties.catalog();
        this.objectMapper = objectMapper;
    }

    /**
     * Accepts an id or a slug, mirroring what the BFF does for the frontend:
     * the caller should not have to know which endpoint owns the slug.
     */
    public UUID resolveContentId(String slugOrId) {
        try {
            return UUID.fromString(slugOrId);
        } catch (IllegalArgumentException notAnId) {
            return fetchContentId("/api/v1/catalog/movies/" + slugOrId)
                .or(() -> fetchContentId("/api/v1/catalog/series/" + slugOrId))
                .orElseThrow(() -> new IllegalArgumentException(
                    "No content found for '%s'".formatted(slugOrId)));
        }
    }

    public void setHlsPath(PipelineTarget.Kind kind, UUID id, String hlsPath) {
        String path = kind == PipelineTarget.Kind.CONTENT
            ? "/api/v1/catalog/admin/contents/" + id + "/hls-path"
            : "/api/v1/catalog/admin/episodes/" + id + "/hls-path";
        String body = writeJson(Map.of("hlsPath", hlsPath));
        HttpResponse<String> response = send(HttpRequest.newBuilder()
            .uri(URI.create(settings.baseUrl() + path))
            .header("Authorization", "Bearer " + accessToken())
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Catalogue rejected the hls path for %s (%d): %s"
                .formatted(id, response.statusCode(), response.body()));
        }
        log.info("Catalogue updated: {} {} -> {}", kind.name().toLowerCase(), id, hlsPath);
    }

    private Optional<UUID> fetchContentId(String path) {
        HttpResponse<String> response = send(HttpRequest.newBuilder()
            .uri(URI.create(settings.baseUrl() + path))
            .header("Accept", "application/json")
            .GET()
            .build());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Catalogue read failed (%d): %s"
                .formatted(response.statusCode(), response.body()));
        }
        try {
            return Optional.of(UUID.fromString(
                objectMapper.readTree(response.body()).get("id").asText()));
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Unreadable catalogue response for " + path, ex);
        }
    }

    /**
     * Client credentials, cached for the life of the job: one token per run is
     * one more than a single-write job needs, and two would be a round trip
     * nobody asked for.
     */
    private String accessToken() {
        if (cachedToken != null) {
            return cachedToken;
        }
        String form = "grant_type=client_credentials"
            + "&client_id=" + encode(settings.token().clientId())
            + "&client_secret=" + encode(settings.token().clientSecret());
        HttpResponse<String> response = send(HttpRequest.newBuilder()
            .uri(URI.create(settings.token().url()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Could not obtain a media token (%d): %s"
                .formatted(response.statusCode(), response.body()));
        }
        try {
            cachedToken = objectMapper.readTree(response.body()).get("access_token").asText();
            return cachedToken;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Unreadable token response", ex);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException("HTTP call failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during an HTTP call", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not serialise the request body", ex);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
