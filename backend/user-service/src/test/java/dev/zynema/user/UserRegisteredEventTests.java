package dev.zynema.user;

import dev.zynema.common.messaging.EventEnvelopes;
import dev.zynema.common.messaging.OutboxRelay;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.UserEvent;
import dev.zynema.user.dto.UserDto;
import dev.zynema.user.service.CurrentUserService;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The user side of Fase 7: JIT provisioning is what publishes
 * {@code UserRegistered}, the only event that carries the email (ADR-0009 the
 * notification projection depends on it).
 */
class UserRegisteredEventTests extends AbstractUserIntegrationTest {

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private OutboxRelay relay;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @BeforeEach
    void reset() {
        resetOutbox();
    }

    @Test
    @DisplayName("provisioning appends UserRegistered in the same transaction and the relay publishes it")
    void provisioningPublishesUserRegistered() {
        UUID subject = UUID.randomUUID();
        String email = "new-%s@zynema.dev".formatted(subject);
        Jwt jwt = Jwt.withTokenValue("user-events-test").header("alg", "none")
            .subject(subject.toString())
            .claim("email", email)
            .claim("email_verified", true)
            .claim("name", "New Viewer")
            .build();

        UserDto user = currentUserService.resolve(jwt);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE subject = ?",
            Long.class, user.id().toString())).isEqualTo(1);

        relay.publishPending();

        ConsumerRecord<String, UserEvent> record = drain(user.id(), 1).get(0);
        assertThat(record.value()).isInstanceOf(UserEvent.UserRegistered.class);
        assertThat(((UserEvent.UserRegistered) record.value()).email()).isEqualTo(email);
        assertThat(EventEnvelopes.of(record).type()).isEqualTo("user.user-registered");
    }

    private List<ConsumerRecord<String, UserEvent>> drain(UUID key, int expected) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "user-events-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonSchemaDeserializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("json.value.type", UserEvent.class.getName());

        List<ConsumerRecord<String, UserEvent>> records = new ArrayList<>();
        try (KafkaConsumer<String, UserEvent> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaTopics.USER_EVENTS));
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.toString().equals(record.key())) {
                        records.add(record);
                    }
                });
            }
        }
        return records;
    }
}
