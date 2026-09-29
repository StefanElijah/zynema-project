package dev.zynema.playback;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
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

    /** A private network so Kafka and the registry can talk to each other. */
    static final Network NETWORK = Network.newNetwork();

    /**
     * A real broker with <strong>two listeners</strong>: the default one for
     * the test JVM (the mapped address) and one on the network alias, which is
     * the address the Schema Registry uses. Same image as the compose stack.
     */
    static final org.testcontainers.kafka.ConfluentKafkaContainer KAFKA =
        new org.testcontainers.kafka.ConfluentKafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka")
            .withListener("kafka:19092");

    /** Real Schema Registry: it registers and compatibility-checks the schemas. */
    static final GenericContainer<?> SCHEMA_REGISTRY =
        new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.6.1"))
            .withNetwork(NETWORK)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
            .withEnv("SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL", "backward")
            .withExposedPorts(8081)
            .waitingFor(Wait.forHttp("/subjects").forPort(8081));

    private static S3Client hlsClient;

    /** Lets the subclasses reset the state the write path owns between tests. */
    @Autowired
    protected JdbcTemplate jdbc;

    static {
        POSTGRES.start();
        REDIS.start();
        MINIO.start();
        DEPENDENCIES.start();
        KAFKA.start();
        SCHEMA_REGISTRY.start();
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
        kafkaProperties(registry);
        // Two events are enough to make snapshotting visible in a test.
        registry.add("zynema.playback.events.snapshot-every", () -> "2");
    }

    /**
     * The Kafka block, mirroring {@code infra/config-repo/application.yml}.
     * Tests do not consume the config server, so the shared messaging
     * configuration has to be declared here as well; only the addresses change.
     */
    private static void kafkaProperties(DynamicPropertyRegistry registry) {
        String registryUrl = "http://localhost:" + SCHEMA_REGISTRY.getMappedPort(8081);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.producer.key-serializer",
            () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer",
            () -> "io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer");
        registry.add("spring.kafka.producer.acks", () -> "all");
        registry.add("spring.kafka.producer.properties.enable.idempotence", () -> "true");
        registry.add("spring.kafka.producer.properties.schema.registry.url", () -> registryUrl);
        registry.add("spring.kafka.producer.properties.auto.register.schemas", () -> "true");
        registry.add("spring.kafka.producer.properties.value.subject.name.strategy",
            () -> "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        registry.add("spring.kafka.consumer.key-deserializer",
            () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer",
            () -> "io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.properties.schema.registry.url", () -> registryUrl);
        // The relay only runs when a test calls it: no scheduler races.
        registry.add("zynema.messaging.outbox.initial-delay", () -> "1h");
    }

    /** Truncates what the write path owns, so streams do not leak between tests. */
    protected void resetEventStore() {
        jdbc.execute("TRUNCATE session_events, session_snapshots, outbox");
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
