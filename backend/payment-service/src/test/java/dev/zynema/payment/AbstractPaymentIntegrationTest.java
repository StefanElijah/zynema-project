package dev.zynema.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Base class for payment integration tests.
 *
 * <p>Runs against a real PostgreSQL, a real Redis and a <strong>real HTTP
 * server</strong> standing in for user-service. The Feign client is pointed at
 * WireMock by URL, so the whole client stack — interceptor, timeouts, error
 * decoder, circuit breaker — is exercised instead of a mocked interface.
 *
 * <p>Containers are started once per JVM (singleton pattern) and reaped by
 * Testcontainers at exit.
 */
@SpringBootTest
public abstract class AbstractPaymentIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** Stands in for user-service. */
    static final WireMockServer USER_SERVICE = new WireMockServer(options().dynamicPort());

    /** A private network so Kafka and the registry can talk to each other. */
    static final org.testcontainers.containers.Network NETWORK =
        org.testcontainers.containers.Network.newNetwork();

    /**
     * A real broker with <strong>two listeners</strong>: the default one for
     * the test JVM (the mapped address) and one on the network alias, which is
     * the address the Schema Registry uses. The image is the same one the
     * compose stack runs, so the broker version is identical in both worlds.
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

    static {
        POSTGRES.start();
        REDIS.start();
        USER_SERVICE.start();
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
        // An explicit URL makes Feign skip service discovery for this client.
        registry.add("spring.cloud.openfeign.client.config.user-service.url",
            () -> "http://localhost:" + USER_SERVICE.port());
        kafkaProperties(registry);
    }

    /**
     * The Kafka block, mirroring {@code infra/config-repo/application.yml}.
     *
     * <p>Tests do not consume the config server (the client is disabled on
     * purpose, Fase 5), so the shared messaging configuration has to be
     * declared here as well. Only the addresses change between the two.
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
        registry.add("spring.kafka.consumer.properties.json.value.type",
            () -> "dev.zynema.events.EventEnvelope");
        registry.add("spring.kafka.retry.topic.attempts", () -> "3");
        registry.add("spring.kafka.retry.topic.max-delay", () -> "1s");
        registry.add("spring.kafka.retry.topic.multiplier", () -> "2");
    }
}
