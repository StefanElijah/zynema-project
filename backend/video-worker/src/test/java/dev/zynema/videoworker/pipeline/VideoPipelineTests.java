package dev.zynema.videoworker.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.videoworker.AbstractWorkerIntegrationTest;
import dev.zynema.videoworker.catalog.CatalogAdminClient;
import dev.zynema.videoworker.media.Transcoder;
import dev.zynema.videoworker.storage.ObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pipeline end to end, minus the FFmpeg binary: a fake transcoder writes a
 * tiny ladder, everything else is real (S3 API, HTTP, key layout, ordering).
 */
class VideoPipelineTests extends AbstractWorkerIntegrationTest {

    private static final String CONTENT_ID = "a1000000-0000-4000-8000-000000000003";
    private static final UUID EPISODE_ID = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final String SOURCE_KEY = "samples/arcane-s01e01.mp4";

    @TempDir
    Path temp;

    private VideoPipeline pipeline(Transcoder transcoder) {
        var properties = properties(temp);
        return new VideoPipeline(properties, new ObjectStorage(rawClient(), properties),
            transcoder, new CatalogAdminClient(properties, new ObjectMapper()));
    }

    @Test
    @DisplayName("a content job downloads, transcodes, uploads and only then tells the catalogue")
    void runsAContentJob() throws Exception {
        uploadSource();
        stubCatalogue(CONTENT_ID);

        JobResult result = pipeline(fakeTranscoder()).run(PipelineTarget.content("dune-part-two"), SOURCE_KEY);

        assertThat(result.masterKey()).isEqualTo("hls/contents/" + CONTENT_ID + "/master.m3u8");
        assertThat(result.uploadedObjects()).isEqualTo(3);
        assertThat(objectExists("hls/contents/" + CONTENT_ID + "/240p/segment_00000.ts")).isTrue();
        CATALOG.verify(putRequestedFor(urlPathEqualTo("/api/v1/catalog/admin/contents/" + CONTENT_ID + "/hls-path"))
            .withRequestBody(equalToJson(
                "{\"hlsPath\": \"hls/contents/" + CONTENT_ID + "/master.m3u8\"}")));
    }

    @Test
    @DisplayName("an episode job keys the render by episode id")
    void runsAnEpisodeJob() throws Exception {
        uploadSource();
        CATALOG.stubFor(post(urlPathEqualTo("/token"))
            .willReturn(okJson("{\"access_token\": \"media-token\"}")));
        CATALOG.stubFor(put(urlPathEqualTo("/api/v1/catalog/admin/episodes/" + EPISODE_ID + "/hls-path"))
            .willReturn(okJson("{}")));

        JobResult result = pipeline(fakeTranscoder()).run(PipelineTarget.episode(EPISODE_ID), SOURCE_KEY);

        assertThat(result.masterKey()).isEqualTo("hls/episodes/" + EPISODE_ID + "/master.m3u8");
        assertThat(objectExists("hls/episodes/" + EPISODE_ID + "/master.m3u8")).isTrue();
    }

    @Test
    @DisplayName("a failed transcode never reaches the catalogue: the content stays 'not ready'")
    void aFailedTranscodeLeavesTheCatalogueUntouched() throws Exception {
        String untouchedId = "a1000000-0000-4000-8000-000000000004";
        uploadSource();
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/movies/dune-part-one"))
            .willReturn(okJson("{\"id\": \"%s\"}".formatted(untouchedId))));

        Transcoder failing = (source, output) -> {
            throw new IllegalStateException("FFmpeg failed with exit code 1");
        };

        assertThatThrownBy(() -> pipeline(failing).run(PipelineTarget.content("dune-part-one"), SOURCE_KEY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exit code 1");

        CATALOG.verify(0, putRequestedFor(
            urlPathEqualTo("/api/v1/catalog/admin/contents/" + untouchedId + "/hls-path")));
        assertThat(objectExists("hls/contents/" + untouchedId + "/master.m3u8")).isFalse();
    }

    @Test
    @DisplayName("a re-run replaces the prefix instead of accumulating segments")
    void reRunsReplaceTheLadder() throws Exception {
        uploadSource();
        stubCatalogue(CONTENT_ID);

        pipeline(fakeTranscoder()).run(PipelineTarget.content("dune-part-two"), SOURCE_KEY);
        pipeline(fakeTranscoder()).run(PipelineTarget.content("dune-part-two"), SOURCE_KEY);

        ListObjectsV2Request list = ListObjectsV2Request.builder()
            .bucket(HLS_BUCKET).prefix("hls/contents/" + CONTENT_ID + "/").build();
        assertThat(rawClient().listObjectsV2(list).contents()).hasSize(3);
    }

    private void uploadSource() {
        rawClient().putObject(PutObjectRequest.builder()
                .bucket(SOURCE_BUCKET).key(SOURCE_KEY).build(),
            RequestBody.fromString("source bytes"));
    }

    private void stubCatalogue(String contentId) {
        CATALOG.stubFor(get(urlPathEqualTo("/api/v1/catalog/movies/dune-part-two"))
            .willReturn(okJson("{\"id\": \"%s\"}".formatted(contentId))));
        CATALOG.stubFor(post(urlPathEqualTo("/token"))
            .willReturn(okJson("{\"access_token\": \"media-token\"}")));
        CATALOG.stubFor(put(urlPathEqualTo("/api/v1/catalog/admin/contents/" + contentId + "/hls-path"))
            .willReturn(okJson("{}")));
    }

    /** Writes the same shape FFmpeg does: a master plus one variant directory. */
    private Transcoder fakeTranscoder() {
        return (source, output) -> {
            try {
                assertThat(source).exists();
                Path variant = Files.createDirectories(output.resolve("240p"));
                Files.writeString(output.resolve("master.m3u8"), "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=464000\n240p/playlist.m3u8\n");
                Files.writeString(variant.resolve("playlist.m3u8"), "#EXTM3U\n#EXTINF:6.0,\nsegment_00000.ts\n");
                Files.write(variant.resolve("segment_00000.ts"), new byte[]{0, 1, 2});
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        };
    }

    private boolean objectExists(String key) {
        try {
            rawClient().headObject(HeadObjectRequest.builder().bucket(HLS_BUCKET).key(key).build());
            return true;
        } catch (Exception missing) {
            return false;
        }
    }
}
