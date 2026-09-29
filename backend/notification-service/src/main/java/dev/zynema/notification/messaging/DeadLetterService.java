package dev.zynema.notification.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.messaging.EventMetadata;
import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.events.EventTypes;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.PaymentEvent;
import dev.zynema.notification.contact.ContactDirectory;
import dev.zynema.notification.notification.NotificationLogWriter;
import dev.zynema.notification.notification.NotificationTemplates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * What happens when the retries are exhausted (ADR-0025): the record is
 * persisted so the poison pill is visible, and a permanently failed welcome
 * email turns into the saga's compensation trigger, {@code NotificationFailed}.
 *
 * <p>The failure notification reuses the source event's id as the notification
 * id: the same failed event is the same failure, so payment's primary key and
 * the log stay idempotent even if the dead-letter path runs twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private static final String DLT_TOPIC_HEADER = "kafka_dlt-original-topic";
    private static final String DLT_ERROR_HEADER = "kafka_dlt-exception-message";
    private static final String WELCOME_TRIGGER =
        EventTypes.forPayload("payment", PaymentEvent.SubscriptionCreated.class.getSimpleName());

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ContactDirectory contacts;
    private final NotificationLogWriter notificationLog;
    private final OutboxRecorder outbox;

    @Transactional
    public void persist(ConsumerRecord<String, Object> record, String consumer) {
        UUID eventId = uuidHeader(record, EventMetadata.HEADER_EVENT_ID);
        String eventType = stringHeader(record, EventMetadata.HEADER_TYPE);
        String originalTopic = header(record, DLT_TOPIC_HEADER);
        if (originalTopic == null) {
            originalTopic = record.topic();
        }
        String error = header(record, DLT_ERROR_HEADER);
        if (error == null) {
            error = "Retries exhausted before the notification could be delivered";
        }

        jdbc.update("""
            INSERT INTO notification_dead_letters
                (id, topic, partition, record_offset, subject, event_type, event_id, payload, error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (topic, partition, record_offset) DO NOTHING
            """, UUID.randomUUID(), originalTopic, record.partition(), record.offset(),
            record.key(), eventType, eventId, json(record.value()), truncate(error, 1900));

        log.error("Dead letter from {} ({} on {}:{}): {}", consumer, eventType, originalTopic,
            record.offset(), error);

        // The handler runs on the dead-letter topic, so the origin is what the
        // type says, not the topic name.
        if (WELCOME_TRIGGER.equals(eventType) && eventId != null) {
            compensate(eventId, record.value(), error);
        }
    }

    /**
     * {@code SubscriptionCreated} is the only payment event notification acts
     * on; if it died here, payment gets told. The userId comes from the payload
     * when it is still typed, and from the raw map when the retry topics
     * delivered it without the declared type — the compensation must not depend
     * on how a record travelled.
     */
    private void compensate(UUID sourceEventId, Object payload, String reason) {
        UUID userId = userIdOf(payload);
        if (userId == null) {
            log.warn("Dead-lettered payment event {} has no readable userId ({}); no compensation emitted",
                sourceEventId, payload == null ? "null" : payload.getClass().getName());
            return;
        }
        ContactDirectory.Contact contact = contacts.find(userId).orElse(null);
        notificationLog.failed(sourceEventId, userId, NotificationTemplates.SUBSCRIPTION_WELCOME,
            contact == null ? null : contact.email(), reason, sourceEventId);
        outbox.append(KafkaTopics.NOTIFICATION_EVENTS, sourceEventId.toString(),
            new NotificationEvent.NotificationFailed(sourceEventId, userId,
                NotificationTemplates.SUBSCRIPTION_WELCOME, reason, Instant.now()));
    }

    private UUID userIdOf(Object payload) {
        if (payload instanceof PaymentEvent.SubscriptionCreated created) {
            return created.userId();
        }
        if (payload instanceof Map<?, ?> map) {
            return uuidValue(map.get("userId"));
        }
        if (payload instanceof JsonNode node) {
            return uuidValue(node.get("userId"));
        }
        if (payload instanceof String json) {
            return userIdFromJson(json);
        }
        if (payload instanceof byte[] bytes) {
            return userIdFromJson(new String(bytes, StandardCharsets.UTF_8));
        }
        return null;
    }

    private UUID userIdFromJson(String json) {
        try {
            return uuidValue(objectMapper.readTree(json).get("userId"));
        } catch (Exception ex) {
            return null;
        }
    }

    private UUID uuidValue(Object raw) {
        Object value = raw instanceof JsonNode node ? node.asText() : raw;
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String json(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return String.valueOf(payload);
        }
    }

    private UUID uuidHeader(ConsumerRecord<?, ?> record, String name) {
        String value = header(record, name);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String stringHeader(ConsumerRecord<?, ?> record, String name) {
        return header(record, name);
    }

    private String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
