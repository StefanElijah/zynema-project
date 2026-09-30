package dev.zynema.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.messaging.EventPublisher;
import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.common.messaging.OutboxRelay;
import dev.zynema.common.messaging.OutboxSerializer;
import dev.zynema.common.messaging.ProcessedEventStore;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka wiring shared by every messaging service (Fase 7).
 *
 * <p>Activated only where the classes are present, like the other optional
 * pieces of this module: a service opts into messaging by adding
 * {@code spring-kafka} and the JSON Schema serializer.
 *
 * <p>The event template is deliberately <strong>separate</strong> from Boot's
 * default one because it is the only thing allowed to know how a message is
 * shaped: the payload goes in the value (so its concrete schema is what the
 * registry checks) and the metadata goes in headers. It inherits every
 * {@code spring.kafka.*} property the shared configuration sets (serialisers,
 * registry URL, subject strategy, idempotence).
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass({KafkaTemplate.class, KafkaJsonSchemaSerializer.class})
public class CommonMessagingAutoConfiguration {

    private static final String OUTBOX_POLL_INTERVAL = "${zynema.messaging.outbox.poll-interval:2s}";
    private static final String OUTBOX_BATCH_SIZE = "${zynema.messaging.outbox.batch-size:100}";

    @Bean
    @ConditionalOnMissingBean(name = "eventProducerFactory")
    public ProducerFactory<String, Object> eventProducerFactory(KafkaProperties properties) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties());
    }

    @Bean
    @ConditionalOnMissingBean(name = "eventKafkaTemplate")
    public KafkaTemplate<String, Object> eventKafkaTemplate(
        ProducerFactory<String, Object> eventProducerFactory) {
        return new KafkaTemplate<>(eventProducerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventPublisher eventPublisher(
        KafkaTemplate<String, Object> eventKafkaTemplate,
        @Value("${spring.application.name:zynema}") String source) {
        return new EventPublisher(eventKafkaTemplate, source);
    }

    /**
     * Consumers that want the idempotency check get it for free, as long as
     * they have a database (and the {@code processed_events} table).
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public ProcessedEventStore processedEventStore(JdbcTemplate jdbcTemplate) {
        return new ProcessedEventStore(jdbcTemplate);
    }

    /**
     * Producers get the outbox: the recorder to append inside their
     * transaction, the relay to publish afterwards. The scheduling that drives
     * the relay is enabled here too, so a service opts in by adding the outbox
     * table, not by remembering an annotation.
     */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    public OutboxSerializer outboxSerializer(ObjectMapper objectMapper) {
        return new OutboxSerializer(objectMapper);
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, OutboxSerializer.class})
    @ConditionalOnMissingBean
    public OutboxRecorder outboxRecorder(JdbcTemplate jdbcTemplate, OutboxSerializer outboxSerializer) {
        return new OutboxRecorder(jdbcTemplate, outboxSerializer);
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, EventPublisher.class})
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(JdbcTemplate jdbcTemplate, OutboxSerializer outboxSerializer,
                                   EventPublisher eventPublisher,
                                   @Value("${spring.application.name:zynema}") String source,
                                   @Value(OUTBOX_BATCH_SIZE) int batchSize) {
        return new OutboxRelay(jdbcTemplate, outboxSerializer, eventPublisher, source, batchSize);
    }

    /** Only the services that actually have a relay need the scheduler. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(OutboxRelay.class)
    @EnableScheduling
    static class OutboxSchedulingConfiguration {
    }
}
