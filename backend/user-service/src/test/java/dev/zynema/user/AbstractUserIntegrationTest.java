package dev.zynema.user;

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

/**
 * Base class for user-service integration tests.
 *
 * <p>Singleton-container pattern: one PostgreSQL, one Kafka broker and one
 * Schema Registry per test JVM, shared by every subclass and reaped by
 * Testcontainers' Ryuk at JVM exit. Redis is not needed: the service does not
 * cache.
 */
@SpringBootTest
public abstract class AbstractUserIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    /** A private network so Kafka and the registry can talk to each other. */
    static final Network NETWORK = Network.newNetwork();

    /**
     * A real broker with two listeners: the mapped one for the test JVM and
     * the network alias the Schema Registry uses. Same image as compose.
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

    @Autowired
    protected JdbcTemplate jdbc;

    static {
        POSTGRES.start();
        KAFKA.start();
        SCHEMA_REGISTRY.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("management.health.redis.enabled", () -> "false");
        kafkaProperties(registry);
    }

    /**
     * The Kafka block, mirroring {@code infra/config-repo/application.yml}:
     * tests do not consume the config server, so the shared producer
     * configuration has to be declared here as well.
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

    /** Truncates what the write path owns, so events do not leak between tests. */
    protected void resetOutbox() {
        jdbc.execute("TRUNCATE outbox");
    }
}
