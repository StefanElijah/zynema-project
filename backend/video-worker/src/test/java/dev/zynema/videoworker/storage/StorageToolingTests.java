package dev.zynema.videoworker.storage;

import dev.zynema.videoworker.AbstractWorkerIntegrationTest;
import dev.zynema.videoworker.config.WorkerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The two utilities that replace MinIO's client tooling: bucket creation and
 * source import.
 */
class StorageToolingTests extends AbstractWorkerIntegrationTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("a sample is imported with the content type a player expects")
    void importsSources() throws Exception {
        Path sample = temp.resolve("sintel-trailer.mp4");
        Files.writeString(sample, "pretend mp4");

        new SourceImporter(rawClient(), properties(temp))
            .importFile(sample, "samples/sintel-trailer.mp4");

        var head = rawClient().headObject(HeadObjectRequest.builder()
            .bucket(SOURCE_BUCKET).key("samples/sintel-trailer.mp4").build());
        assertThat(head.contentType()).isEqualTo("video/mp4");
        assertThat(head.metadata()).containsEntry("size", String.valueOf(sample.toFile().length()));
    }

    @Test
    @DisplayName("storage initialisation creates missing buckets and is safe to repeat")
    void createsBucketsIdempotently() {
        WorkerProperties fresh = new WorkerProperties(
            new WorkerProperties.Storage(minioEndpoint(), ACCESS_KEY, SECRET_KEY,
                "fresh-sources", "fresh-hls", true),
            properties(temp).catalog(),
            properties(temp).ffmpeg(),
            temp.toString());
        BucketInitializer initializer = new BucketInitializer(rawClient(), fresh);

        initializer.initialize();
        initializer.initialize();

        assertThatCode(() -> rawClient().headBucket(
            HeadBucketRequest.builder().bucket("fresh-sources").build())).doesNotThrowAnyException();
        assertThatCode(() -> rawClient().headBucket(
            HeadBucketRequest.builder().bucket("fresh-hls").build())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("importing a file that does not exist fails before touching S3")
    void rejectsMissingFiles() {
        SourceImporter importer = new SourceImporter(rawClient(), properties(temp));

        assertThatCode(() -> importer.importFile(temp.resolve("nope.mp4"), "samples/nope.mp4"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not exist");
    }
}
