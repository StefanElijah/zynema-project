package dev.zynema.notification.notification;

import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.common.messaging.ProcessedEventStore;
import dev.zynema.events.EventEnvelope;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.PaymentEvent;
import dev.zynema.notification.contact.ContactDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The notification step of the choreographed saga (ADR-0007):
 * {@code SubscriptionCreated} → welcome email → {@code NotificationSent}.
 *
 * <p>The email is the one step that cannot be transactional, so the order is
 * explicit: claim, send, then record the log and the event in the same
 * transaction. A crash after the send duplicates the email on retry — mail is
 * at-least-once here — while the log and the event only exist if the
 * transaction commits.
 *
 * <p>A missing contact is not silently skipped: it means the registration has
 * not been projected yet, the retry window is the right place to wait for it,
 * and after the attempts are exhausted the dead-letter path asks payment for
 * the compensation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeEmailService {

    public static final String HANDLER = "subscription-welcome-email";

    private final ProcessedEventStore processedEvents;
    private final ContactDirectory contacts;
    private final NotificationTemplates templates;
    private final EmailSender emailSender;
    private final NotificationLogWriter notificationLog;
    private final OutboxRecorder outbox;

    @Transactional
    public void onSubscriptionCreated(EventEnvelope<PaymentEvent> envelope) {
        if (!(envelope.payload() instanceof PaymentEvent.SubscriptionCreated created)) {
            return;
        }
        if (!processedEvents.isNew(envelope, HANDLER)) {
            return;
        }

        ContactDirectory.Contact contact = contacts.find(created.userId()).orElseThrow(() ->
            new IllegalStateException("No contact for user " + created.userId()
                + ": UserRegistered has not been projected yet"));

        NotificationTemplates.EmailContent content = templates.render(
            NotificationTemplates.SUBSCRIPTION_WELCOME,
            Map.of("displayName", contact.displayName() == null ? "" : contact.displayName(),
                "planCode", created.planCode()));

        emailSender.send(contact.email(), content);

        UUID notificationId = UUID.randomUUID();
        notificationLog.sent(notificationId, created.userId(),
            NotificationTemplates.SUBSCRIPTION_WELCOME, contact.email(), envelope.eventId());
        outbox.append(KafkaTopics.NOTIFICATION_EVENTS, notificationId.toString(),
            new NotificationEvent.NotificationSent(notificationId, created.userId(),
                NotificationTemplates.SUBSCRIPTION_WELCOME, contact.email(), Instant.now()));
    }
}
