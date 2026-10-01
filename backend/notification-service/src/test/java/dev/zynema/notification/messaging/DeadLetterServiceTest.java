package dev.zynema.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.messaging.EventMetadata;
import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationCommand;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.PaymentEvent;
import dev.zynema.notification.contact.ContactDirectory;
import dev.zynema.notification.notification.NotificationLogWriter;
import dev.zynema.notification.notification.NotificationTemplates;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The dead-letter path: persist the poison pill and, when the failure is the
 * saga's, emit the compensation trigger with the source event's own id so a
 * redelivery cannot duplicate it.
 */
class DeadLetterServiceTest {

    private static final String DLT_TOPIC = "kafka_dlt-original-topic";
    private static final String DLT_ERROR = "kafka_dlt-exception-message";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ContactDirectory contacts = mock(ContactDirectory.class);
    private final NotificationLogWriter notificationLog = mock(NotificationLogWriter.class);
    private final OutboxRecorder outbox = mock(OutboxRecorder.class);

    private final DeadLetterService service = new DeadLetterService(
        jdbc, new ObjectMapper().findAndRegisterModules(), contacts, notificationLog, outbox);

    private final UUID eventId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private final PaymentEvent.SubscriptionCreated created = new PaymentEvent.SubscriptionCreated(
        UUID.randomUUID(), userId, UUID.randomUUID(), "standard", new BigDecimal("9.99"),
        "EUR", Instant.now());

    @Test
    void persistsThePoisonPillAndCompensatesTheWelcomeFailure() {
        ContactDirectory.Contact contact = contact("demo@zynema.dev");
        when(contacts.find(userId)).thenReturn(Optional.of(contact));
        ConsumerRecord<String, Object> record = record(created, "payment.subscription-created");
        record.headers().add(DLT_TOPIC, "zynema.payment.events".getBytes(StandardCharsets.UTF_8));
        record.headers().add(DLT_ERROR, "mail server down".getBytes(StandardCharsets.UTF_8));

        service.persist(record, "notification-service");

        verify(jdbc).update(contains("INSERT INTO notification_dead_letters"),
            any(UUID.class), eq("zynema.payment.events"), eq(0), eq(7L), eq("user-1"),
            eq("payment.subscription-created"), eq(eventId), anyString(), eq("mail server down"));
        verify(notificationLog).failed(eventId, userId, NotificationTemplates.SUBSCRIPTION_WELCOME,
            "demo@zynema.dev", "mail server down", eventId);

        ArgumentCaptor<NotificationEvent.NotificationFailed> event =
            ArgumentCaptor.forClass(NotificationEvent.NotificationFailed.class);
        verify(outbox).append(eq(KafkaTopics.NOTIFICATION_EVENTS), eq(eventId.toString()), event.capture());
        assertThat(event.getValue().notificationId()).isEqualTo(eventId);
        assertThat(event.getValue().userId()).isEqualTo(userId);
        assertThat(event.getValue().reason()).isEqualTo("mail server down");
    }

    @Test
    void fallsBackToTheRecordTopicAndADefaultReason() {
        when(contacts.find(userId)).thenReturn(Optional.empty());
        ConsumerRecord<String, Object> record = record(Map.of("userId", userId.toString()),
            "payment.subscription-created");

        service.persist(record, "notification-service");

        verify(jdbc).update(contains("INSERT INTO notification_dead_letters"),
            any(UUID.class), eq("zynema.notification.events-dlt"), eq(0), eq(7L), eq("user-1"),
            eq("payment.subscription-created"), eq(eventId), anyString(),
            eq("Retries exhausted before the notification could be delivered"));
        verify(notificationLog).failed(eventId, userId, NotificationTemplates.SUBSCRIPTION_WELCOME,
            null, "Retries exhausted before the notification could be delivered", eventId);
    }

    @Test
    void readsAUserIdFromAJsonString() {
        when(contacts.find(userId)).thenReturn(Optional.empty());
        String json = "{\"userId\":\"%s\"}".formatted(userId);

        service.persist(record(json, "payment.subscription-created"), "notification-service");

        verify(notificationLog).failed(eq(eventId), eq(userId), anyString(), any(), anyString(), eq(eventId));
    }

    @Test
    void readsAUserIdFromRawBytes() {
        when(contacts.find(userId)).thenReturn(Optional.empty());
        byte[] json = "{\"userId\":\"%s\"}".formatted(userId).getBytes(StandardCharsets.UTF_8);

        service.persist(record(json, "payment.subscription-created"), "notification-service");

        verify(notificationLog).failed(eq(eventId), eq(userId), anyString(), any(), anyString(), eq(eventId));
    }

    @Test
    void readsAUserIdFromAJsonNode() {
        when(contacts.find(userId)).thenReturn(Optional.empty());
        var node = new ObjectMapper().createObjectNode().put("userId", userId.toString());

        service.persist(record(node, "payment.subscription-created"), "notification-service");

        verify(notificationLog).failed(eq(eventId), eq(userId), anyString(), any(), anyString(), eq(eventId));
    }

    @Test
    void doesNotCompensateWhenThePaymentPayloadHasNoUserId() {
        service.persist(record(42, "payment.subscription-created"), "notification-service");

        verify(outbox, never()).append(any(), any(), any());
        verify(notificationLog, never()).failed(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoresAWelcomeEventWithoutAnEventIdHeader() {
        ConsumerRecord<String, Object> record = record(created, "payment.subscription-created");
        record.headers().remove(EventMetadata.HEADER_EVENT_ID);

        service.persist(record, "notification-service");

        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void ignoresAnUnreadableEventIdHeader() {
        ConsumerRecord<String, Object> record = record(created, "payment.subscription-created");
        record.headers().remove(EventMetadata.HEADER_EVENT_ID);
        record.headers().add(EventMetadata.HEADER_EVENT_ID, "not-a-uuid".getBytes(StandardCharsets.UTF_8));

        service.persist(record, "notification-service");

        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void compensatesADeadSendNotificationCommand() {
        UUID notificationId = UUID.randomUUID();
        ContactDirectory.Contact contact = contact("demo@zynema.dev");
        when(contacts.find(userId)).thenReturn(Optional.of(contact));
        NotificationCommand.SendNotification command =
            new NotificationCommand.SendNotification(notificationId, userId, "subscription-welcome", Map.of());

        service.persist(record(command, "notification.send-notification"), "notification-service");

        verify(notificationLog).failed(notificationId, userId, "subscription-welcome",
            "demo@zynema.dev", "Retries exhausted before the notification could be delivered", null);
        ArgumentCaptor<NotificationEvent.NotificationFailed> event =
            ArgumentCaptor.forClass(NotificationEvent.NotificationFailed.class);
        verify(outbox).append(eq(KafkaTopics.NOTIFICATION_EVENTS),
            eq(notificationId.toString()), event.capture());
        assertThat(event.getValue().notificationId()).isEqualTo(notificationId);
    }

    @Test
    void readsACommandFromAMap() {
        UUID notificationId = UUID.randomUUID();
        when(contacts.find(userId)).thenReturn(Optional.empty());

        service.persist(record(Map.of(
            "notificationId", notificationId.toString(),
            "userId", userId.toString(),
            "template", "subscription-welcome"), "notification.send-notification"),
            "notification-service");

        verify(outbox).append(eq(KafkaTopics.NOTIFICATION_EVENTS), eq(notificationId.toString()), any());
    }

    @Test
    void readsACommandFromAJsonNode() {
        UUID notificationId = UUID.randomUUID();
        var node = new ObjectMapper().createObjectNode()
            .put("notificationId", notificationId.toString())
            .put("userId", userId.toString())
            .put("template", "subscription-welcome");

        service.persist(record(node, "notification.send-notification"), "notification-service");

        verify(outbox).append(eq(KafkaTopics.NOTIFICATION_EVENTS), eq(notificationId.toString()), any());
    }

    @Test
    void staysSilentWhenTheCommandCannotBeRead() {
        service.persist(record(Map.of("template", "subscription-welcome"), "notification.send-notification"),
            "notification-service");

        verify(outbox, never()).append(any(), any(), any());
    }

    @Test
    void truncatesALongErrorToFitTheColumn() {
        ConsumerRecord<String, Object> record = record(42, "other.type");
        record.headers().add(DLT_ERROR, "x".repeat(2000).getBytes(StandardCharsets.UTF_8));

        service.persist(record, "notification-service");

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("INSERT INTO notification_dead_letters"),
            any(UUID.class), anyString(), eq(0), anyLong(), any(), any(), any(), anyString(), error.capture());
        assertThat(error.getValue()).hasSize(1900);
    }

    @Test
    void storesSomethingWhenThePayloadCannotBeSerialised() {
        service.persist(record(new Object(), "other.type"), "notification-service");

        verify(jdbc).update(contains("INSERT INTO notification_dead_letters"),
            any(UUID.class), anyString(), eq(0), anyLong(), any(), any(), any(),
            contains("java.lang.Object"), anyString());
    }

    private ContactDirectory.Contact contact(String email) {
        ContactDirectory.Contact contact = mock(ContactDirectory.Contact.class);
        when(contact.email()).thenReturn(email);
        return contact;
    }

    private ConsumerRecord<String, Object> record(Object payload, String eventType) {
        ConsumerRecord<String, Object> record =
            new ConsumerRecord<>("zynema.notification.events-dlt", 0, 7L, "user-1", payload);
        record.headers().add(EventMetadata.HEADER_EVENT_ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventMetadata.HEADER_TYPE, eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
