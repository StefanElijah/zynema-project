package dev.zynema.user.messaging;

import dev.zynema.events.UserCommand;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The command listener's factory declares {@code json.value.type} at the
 * consumer level (ADR-0025, corrected in ADR-0028): retry containers are built
 * from this factory, and a per-listener property is not reapplied to them.
 *
 * <p>The typed consumer factory is not a bean on purpose: Boot's default
 * container factory needs exactly one {@code ConsumerFactory} bean.
 */
@Configuration(proxyBeanMethods = false)
public class ListenerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> userCommandsListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer configurer, KafkaProperties properties) {
        Map<String, Object> consumerProperties = new HashMap<>(properties.buildConsumerProperties());
        consumerProperties.put("json.value.type", UserCommand.class.getName());

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, new DefaultKafkaConsumerFactory<>(consumerProperties));
        return factory;
    }
}
