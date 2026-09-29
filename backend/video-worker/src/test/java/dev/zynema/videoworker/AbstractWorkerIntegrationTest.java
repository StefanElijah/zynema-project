package dev.zynema.videoworker;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.zynema.videoworker.config.Rendition;
import dev.zynema.videoworker.config.WorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Base for worker integration tests: a real MinIO (the same image the compose
 * profile runs) and a real HTTP server standing in for catalog-service.
 *
 * <p>The AWS SDK is exercised against the real S3 API, not a mock: content
 * types, prefix deletion and path-style addressing are exactly the parts that
 * a mocked client would happily lie about.
 */
public abstract class AbstractWorkerIntegrationTest {

    protected static final String ACCESS_KEY = "zynema-admin";
    protected static final String SECRET_KEY = "change-me-in-real-life";
    protected static final String SOURCE_BUCKET = "zynema-videos";
    protected static final String HLS_BUCKET = "zynema-hls";

    /**
     * MinIO stopped publishing community images to Docker Hub and quay, and the
     * official AIStor image refuses to serve without a licence. Chainguard's
     * source-built image is the remaining public one; pinned by digest, because
     * a floating tag on an infrastructure dependency is how a pipeline breaks
     * on a day nobody changed anything.
     */
    protected static final String MINIO_IMAGE =
        "cgr.dev/chainguard/minio@sha256:71674988a1c7ddd5724928633199152b11e4ddefd6c6ce2d60772ff4a8f22ca9";

    protected static final GenericContainer<?> MINIO =
        new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server /data")
            // Chainguard defaults to a nonroot user that cannot write /data in
            // an anonymous volume; this is a throwaway test container.
            .withCreateContainerCmdModifier(cmd -> cmd.withUser("0:0"))
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    protected static final WireMockServer CATALOG = new WireMockServer(options().dynamicPort());

    static {
        MINIO.start();
        CATALOG.start();
    }

    private static S3Client sharedClient;

    @BeforeEach
    void prepare() {
        CATALOG.resetAll();
        createBuckets();
    }

    protected static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    protected static S3Client rawClient() {
        if (sharedClient == null) {
            sharedClient = S3Client.builder()
                .endpointOverride(URI.create(minioEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        }
        return sharedClient;
    }

    protected WorkerProperties properties(Path workspace) {
        return new WorkerProperties(
            new WorkerProperties.Storage(minioEndpoint(), ACCESS_KEY, SECRET_KEY,
                SOURCE_BUCKET, HLS_BUCKET, true),
            new WorkerProperties.Catalog("http://localhost:" + CATALOG.port(),
                new WorkerProperties.Catalog.Token(
                    "http://localhost:" + CATALOG.port() + "/token", "zynema-media", "secret")),
            new WorkerProperties.Ffmpeg("ffmpeg", 6, Duration.ofMinutes(1), List.of(
                new Rendition("240p", 426, 240, 400, 64, null, null),
                new Rendition("720p", 1280, 720, 2800, 128, null, null))),
            workspace.toString());
    }

    private void createBuckets() {
        for (String bucket : List.of(SOURCE_BUCKET, HLS_BUCKET)) {
            try {
                rawClient().createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (Exception alreadyThere) {
                // The container survives between test classes; buckets do not matter then.
            }
        }
    }
}
