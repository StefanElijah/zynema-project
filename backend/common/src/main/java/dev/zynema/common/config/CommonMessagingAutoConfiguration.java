package dev.zynema.common.config;

import dev.zynema.common.messaging.EventPublisher;
import dev.zynema.common.messaging.ProcessedEventStore;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
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
@AutoConfiguration
@ConditionalOnClass({KafkaTemplate.class, KafkaJsonSchemaSerializer.class})
public class CommonMessagingAutoConfiguration {

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
}
