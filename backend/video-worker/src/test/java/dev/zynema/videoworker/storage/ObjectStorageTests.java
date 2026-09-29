package dev.zynema.videoworker.storage;

import dev.zynema.videoworker.AbstractWorkerIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageTests extends AbstractWorkerIntegrationTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("downloads a source and uploads a ladder with HLS-friendly content types")
    void movesObjectsThroughTheRealS3Api() throws Exception {
        String sourceKey = "samples/test-source.mp4";
        rawClient().putObject(PutObjectRequest.builder()
                .bucket(SOURCE_BUCKET).key(sourceKey).build(),
            RequestBody.fromString("fake video bytes"));

        ObjectStorage storage = new ObjectStorage(rawClient(), properties(temp));

        Path downloaded = storage.download(sourceKey, temp.resolve("work/source.mp4"));
        assertThat(Files.readString(downloaded)).isEqualTo("fake video bytes");

        Path ladder = temp.resolve("ladder");
        Files.createDirectories(ladder.resolve("240p"));
        Files.writeString(ladder.resolve("master.m3u8"), "#EXTM3U\n240p/playlist.m3u8\n");
        Files.writeString(ladder.resolve("240p/playlist.m3u8"), "#EXTM3U\nsegment_00000.ts\n");
        Files.write(ladder.resolve("240p/segment_00000.ts"), new byte[]{1, 2, 3});

        int uploaded = storage.upload("hls/contents/abc", ladder);

        assertThat(uploaded).isEqualTo(3);
        assertThat(contentTypeOf("hls/contents/abc/master.m3u8")).isEqualTo("application/vnd.apple.mpegurl");
        assertThat(contentTypeOf("hls/contents/abc/240p/playlist.m3u8")).isEqualTo("application/vnd.apple.mpegurl");
        assertThat(contentTypeOf("hls/contents/abc/240p/segment_00000.ts")).isEqualTo("video/mp2t");
        assertThat(Files.exists(downloaded)).isTrue();
    }

    @Test
    @DisplayName("uploading replaces the prefix: no segment survives a re-render")
    void replacesThePreviousLadder() throws Exception {
        ObjectStorage storage = new ObjectStorage(rawClient(), properties(temp));
        Path ladder = temp.resolve("ladder");
        Files.createDirectories(ladder.resolve("240p"));
        Files.writeString(ladder.resolve("master.m3u8"), "#EXTM3U\n");
        Files.writeString(ladder.resolve("240p/segment_00000.ts"), "old");
        storage.upload("hls/contents/abc", ladder);

        Files.delete(ladder.resolve("240p/segment_00000.ts"));
        Files.writeString(ladder.resolve("240p/segment_00000.ts"), "new");

        int removed = storage.deletePrefix("hls/contents/abc");
        storage.upload("hls/contents/abc", ladder);

        assertThat(removed).isEqualTo(2);
        ListObjectsV2Request list = ListObjectsV2Request.builder()
            .bucket(HLS_BUCKET).prefix("hls/contents/abc/").build();
        assertThat(rawClient().listObjectsV2(list).contents())
            .extracting(object -> object.key())
            .containsExactlyInAnyOrder("hls/contents/abc/master.m3u8", "hls/contents/abc/240p/segment_00000.ts");
        assertThat(Files.readString(ladder.resolve("240p/segment_00000.ts"))).isEqualTo("new");
    }

    private String contentTypeOf(String key) {
        return rawClient().headObject(HeadObjectRequest.builder()
            .bucket(HLS_BUCKET).key(key).build()).contentType();
    }
}
