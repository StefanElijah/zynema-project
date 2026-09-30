package dev.zynema.payment.messaging;

import dev.zynema.events.NotificationEvent;
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
 * {@code json.value.type} (ADR-0025, corrected in ADR-0028): retry containers
 * are built from the listener's factory, and a per-listener property is not
 * reapplied to them. Payment now consumes three domains — the compensation
 * reply, the saga trigger and the saga's role replies.
 *
 * <p>The typed consumer factories are not beans on purpose: Boot's default
 * container factory needs exactly one {@code ConsumerFactory} bean.
 */
@Configuration(proxyBeanMethods = false)
public class ListenerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> notificationEventsListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, KafkaProperties properties) {
        return containerFactory(configurer, typedConsumerFactory(properties, NotificationEvent.class.getName()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> paymentEventsListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, KafkaProperties properties) {
        return containerFactory(configurer, typedConsumerFactory(properties, PaymentEvent.class.getName()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> userEventsListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, KafkaProperties properties) {
        return containerFactory(configurer, typedConsumerFactory(properties, UserEvent.class.getName()));
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
