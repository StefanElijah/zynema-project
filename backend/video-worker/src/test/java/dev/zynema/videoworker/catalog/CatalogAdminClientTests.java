package dev.zynema.videoworker.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.videoworker.AbstractWorkerIntegrationTest;
import dev.zynema.videoworker.pipeline.PipelineTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogAdminClientTests extends AbstractWorkerIntegrationTest {

    private static final String CONTENT_ID = "a1000000-0000-4000-8000-000000000003";
    private static final UUID EPISODE_ID = UUID.fromString("e0000000-0000-4000-8000-000000000001");

    @TempDir
    Path temp;

    private CatalogAdminClient client() {
        return new CatalogAdminClient(properties(temp), new ObjectMapper());
    }

    @Test
    @DisplayName("a slug is resolved through the public catalogue, movies first")
    void resolvesSlugs() {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/movies/arcane"))
            .willReturn(aResponse().withStatus(404)));
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/series/arcane"))
            .willReturn(okJson("{\"id\": \"%s\"}".formatted(CONTENT_ID))));

        assertThat(client().resolveContentId("arcane")).isEqualTo(UUID.fromString(CONTENT_ID));
        CATALOG.verify(getRequestedFor(urlPathEqualTo("/api/v1/catalog/series/arcane")));
    }

    @Test
    @DisplayName("an id needs no lookup at all")
    void acceptsIdsWithoutHttp() {
        assertThat(client().resolveContentId(CONTENT_ID)).isEqualTo(UUID.fromString(CONTENT_ID));
        CATALOG.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/catalog/movies/" + CONTENT_ID)));
    }

    @Test
    @DisplayName("the write authenticates as a machine and sends the master key")
    void authenticatesWithClientCredentials() {
        CATALOG.stubFor(post(urlPathEqualTo("/token"))
            .willReturn(okJson("{\"access_token\": \"media-token\", \"expires_in\": 300}")));
        CATALOG.stubFor(put(urlPathEqualTo("/api/v1/catalog/admin/contents/" + CONTENT_ID + "/hls-path"))
            .willReturn(okJson("{}")));

        client().setHlsPath(PipelineTarget.Kind.CONTENT, UUID.fromString(CONTENT_ID),
            "hls/contents/" + CONTENT_ID + "/master.m3u8");

        CATALOG.verify(postRequestedFor(urlPathEqualTo("/token"))
            .withRequestBody(equalTo("grant_type=client_credentials&client_id=zynema-media&client_secret=secret")));
        CATALOG.verify(putRequestedFor(urlPathEqualTo("/api/v1/catalog/admin/contents/" + CONTENT_ID + "/hls-path"))
            .withHeader("Authorization", equalTo("Bearer media-token"))
            .withRequestBody(equalToJson(
                "{\"hlsPath\": \"hls/contents/" + CONTENT_ID + "/master.m3u8\"}")));
    }

    @Test
    @DisplayName("episode paths go to the episode endpoint")
    void updatesEpisodePaths() {
        CATALOG.stubFor(post(urlPathEqualTo("/token"))
            .willReturn(okJson("{\"access_token\": \"media-token\"}")));
        CATALOG.stubFor(put(urlPathEqualTo("/api/v1/catalog/admin/episodes/" + EPISODE_ID + "/hls-path"))
            .willReturn(okJson("{}")));

        client().setHlsPath(PipelineTarget.Kind.EPISODE, EPISODE_ID,
            "hls/episodes/" + EPISODE_ID + "/master.m3u8");

        CATALOG.verify(putRequestedFor(urlPathEqualTo("/api/v1/catalog/admin/episodes/" + EPISODE_ID + "/hls-path")));
    }

    @Test
    @DisplayName("a rejected update fails with the catalogue's own message")
    void surfacesRejections() {
        CATALOG.stubFor(post(urlPathEqualTo("/token"))
            .willReturn(okJson("{\"access_token\": \"media-token\"}")));
        CATALOG.stubFor(put(urlPathEqualTo("/api/v1/catalog/admin/contents/" + CONTENT_ID + "/hls-path"))
            .willReturn(aResponse().withStatus(404).withBody("{\"message\":\"Content not found\"}")));

        assertThatThrownBy(() -> client().setHlsPath(PipelineTarget.Kind.CONTENT,
            UUID.fromString(CONTENT_ID), "hls/contents/x/master.m3u8"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Content not found");
    }

    @Test
    @DisplayName("an unknown slug is a clear error before any transcode happens")
    void unknownSlugFailsFast() {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/movies/nope"))
            .willReturn(aResponse().withStatus(404)));
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/series/nope"))
            .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client().resolveContentId("nope"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No content found for 'nope'");
    }
}
