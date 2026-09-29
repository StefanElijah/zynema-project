package dev.zynema.playback;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.net.URI;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Base class for playback integration tests.
 *
 * <p>PostgreSQL, Redis, a real HTTP server standing in for the three
 * dependencies (user-service, catalog-service, payment-service) and a real
 * MinIO standing in for the storage edge. Pointing the Feign clients and the
 * S3 client at real servers exercises the whole stack: shared interceptor,
 * timeouts, error decoder, circuit breaker, explicit degradation rules and —
 * for the stream endpoints — real presigned URLs a browser would accept.
 */
@SpringBootTest
public abstract class AbstractPlaybackIntegrationTest {

    static final String HLS_BUCKET = "zynema-hls";
    static final String MINIO_ACCESS_KEY = "zynema-admin";
    static final String MINIO_SECRET_KEY = "change-me-in-real-life";

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** Stands in for the storage: same image and digest as the compose profile. */
    static final GenericContainer<?> MINIO =
        new GenericContainer<>(DockerImageName.parse(
            "cgr.dev/chainguard/minio@sha256:71674988a1c7ddd5724928633199152b11e4ddefd6c6ce2d60772ff4a8f22ca9"))
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withCommand("server /data")
            .withCreateContainerCmdModifier(cmd -> cmd.withUser("0:0"))
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    /** Stands in for user-service, catalog-service and payment-service. */
    static final WireMockServer DEPENDENCIES = new WireMockServer(options().dynamicPort());

    private static S3Client hlsClient;

    static {
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
        DEPENDENCIES.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        String stubUrl = "http://localhost:" + DEPENDENCIES.port();
        registry.add("spring.cloud.openfeign.client.config.user-service.url", () -> stubUrl);
        registry.add("spring.cloud.openfeign.client.config.catalog-service.url", () -> stubUrl);
        registry.add("spring.cloud.openfeign.client.config.payment-service.url", () -> stubUrl);
        registry.add("zynema.playback.storage.endpoint", () -> storageEndpoint());
        registry.add("zynema.playback.storage.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("zynema.playback.storage.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("zynema.playback.storage.hls-bucket", () -> HLS_BUCKET);
        // The public edge a browser would use; the tests assert URLs carry it.
        registry.add("zynema.playback.storage.public-base-url", () -> "http://localhost:8090/minio");
        registry.add("zynema.playback.storage.segment-ttl", () -> "60s");
    }

    protected static String storageEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    /** A raw client for seeding renditions, shared by every test in the JVM. */
    protected static S3Client hlsClient() {
        if (hlsClient == null) {
            hlsClient = S3Client.builder()
                .endpointOverride(URI.create(storageEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
            try {
                hlsClient.createBucket(CreateBucketRequest.builder().bucket(HLS_BUCKET).build());
            } catch (Exception alreadyThere) {
                // Shared container between test classes.
            }
        }
        return hlsClient;
    }

    protected static void seedObject(String key, String content, String contentType) {
        hlsClient().putObject(PutObjectRequest.builder()
            .bucket(HLS_BUCKET).key(key).contentType(contentType).build(),
            RequestBody.fromString(content));
    }
}
