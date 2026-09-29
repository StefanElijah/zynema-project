package dev.zynema.notification.messaging;

import dev.zynema.events.NotificationCommand;
import dev.zynema.events.PaymentEvent;
import dev.zynema.events.UserEvent;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * One container factory per consumed domain, each with its own declared
 * {@code json.value.type} (ADR-0025).
 *
 * <p>The declared type lives on the <strong>consumer factory</strong>, not in
 * {@code @KafkaListener(properties=…)}: the retry machinery builds its
 * containers from the listener's factory, and a per-listener property is not
 * reapplied there. A retry that deserialised into a map would quietly stop
 * matching the event type — the failure this service can least afford.
 *
 * <p>The consumer factories are deliberately not beans: Boot's auto-configured
 * container factory needs exactly one {@code ConsumerFactory} bean, and two
 * extra ones would leave it guessing.
 */
@Configuration(proxyBeanMethods = false)
public class ListenerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> userEventsListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, KafkaProperties properties) {
        return containerFactory(configurer, typedConsumerFactory(properties, UserEvent.class.getName()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> paymentEventsListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, KafkaProperties properties) {
        return containerFactory(configurer, typedConsumerFactory(properties, PaymentEvent.class.getName()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> notificationCommandsListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, KafkaProperties properties) {
        return containerFactory(configurer, typedConsumerFactory(properties, NotificationCommand.class.getName()));
    }

    private ConcurrentKafkaListenerContainerFactory<Object, Object> containerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
        ConsumerFactory<Object, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        return factory;
    }

    private DefaultKafkaConsumerFactory<Object, Object> typedConsumerFactory(KafkaProperties properties, String type) {
        Map<String, Object> consumerProperties = new HashMap<>(properties.buildConsumerProperties());
        consumerProperties.put("json.value.type", type);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }
}
