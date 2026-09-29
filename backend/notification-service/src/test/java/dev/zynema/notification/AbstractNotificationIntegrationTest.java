package dev.zynema.notification;

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

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for notification integration tests: PostgreSQL, a real broker and
 * Schema Registry, and a real MailHog. The whole point of this service is the
 * trip through Kafka into a mailbox, so both ends are real.
 */
@SpringBootTest
public abstract class AbstractNotificationIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static final Network NETWORK = Network.newNetwork();

    /** Two listeners: the mapped one for the JVM, the alias for the registry. */
    static final org.testcontainers.kafka.ConfluentKafkaContainer KAFKA =
        new org.testcontainers.kafka.ConfluentKafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka")
            .withListener("kafka:19092");

    static final GenericContainer<?> SCHEMA_REGISTRY =
        new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.6.1"))
            .withNetwork(NETWORK)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
            .withEnv("SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL", "backward")
            .withExposedPorts(8081)
            .waitingFor(Wait.forHttp("/subjects").forPort(8081));

    /** The dev SMTP server: accepts everything and its API exposes the mailbox. */
    static final GenericContainer<?> MAILHOG =
        new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:latest"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v2/messages").forPort(8025));

    @Autowired
    protected JdbcTemplate jdbc;

    static {
        POSTGRES.start();
        KAFKA.start();
        SCHEMA_REGISTRY.start();
        MAILHOG.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("spring.mail.host", MAILHOG::getHost);
        registry.add("spring.mail.port", () -> MAILHOG.getMappedPort(1025));
        kafkaProperties(registry);
        // The relay only runs when a test calls it: no scheduler races.
        registry.add("zynema.messaging.outbox.initial-delay", () -> "1h");
        // Retries are cheap in tests: two attempts and then the dead letter.
        registry.add("spring.kafka.retry.topic.attempts", () -> "2");
        registry.add("spring.kafka.retry.topic.max-delay", () -> "500ms");
        registry.add("spring.kafka.retry.topic.multiplier", () -> "2");
    }

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
    }

    /** Truncates what this service owns, so streams do not leak between tests. */
    protected void resetNotificationState() {
        jdbc.execute("TRUNCATE contacts, notification_log, notification_dead_letters, processed_events, outbox");
    }

    protected void await(String description, BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for " + description, ex);
            }
        }
        assertThat(false).as("Timed out waiting for %s", description).isTrue();
    }

    protected String mailhogApiUrl() {
        return "http://" + MAILHOG.getHost() + ":" + MAILHOG.getMappedPort(8025);
    }
}
