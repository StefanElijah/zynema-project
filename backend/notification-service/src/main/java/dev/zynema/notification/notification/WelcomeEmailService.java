package dev.zynema.notification.notification;

import dev.zynema.common.messaging.OutboxRecorder;
import dev.zynema.common.messaging.ProcessedEventStore;
import dev.zynema.events.EventEnvelope;
import dev.zynema.events.KafkaTopics;
import dev.zynema.events.NotificationCommand;
import dev.zynema.events.NotificationEvent;
import dev.zynema.events.PaymentEvent;
import dev.zynema.notification.contact.ContactDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The notification step of the onboarding, in both coordination styles
 * (ADR-0007, ADR-0028): the choreographed flow triggers it with
 * {@code SubscriptionCreated}, the orchestrated one with a
 * {@code SendNotification} command. The work is identical — only who decides
 * changes — so it lives in one method.
 *
 * <p>The email is the one step that cannot be transactional, so the order is
 * explicit: claim, send, then record the log and the event in the same
 * transaction. A crash after the send duplicates the email on retry — mail is
 * at-least-once here — while the log and the event only exist if the
 * transaction commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeEmailService {

    public static final String HANDLER = "subscription-welcome-email";
    public static final String COMMAND_HANDLER = "send-notification-command";

    private final ProcessedEventStore processedEvents;
    private final ContactDirectory contacts;
    private final NotificationTemplates templates;
    private final EmailSender emailSender;
    private final NotificationLogWriter notificationLog;
    private final OutboxRecorder outbox;

    /** The choreographed trigger: the payment event itself asks for the email. */
    @Transactional
    public void onSubscriptionCreated(EventEnvelope<PaymentEvent> envelope) {
        if (!(envelope.payload() instanceof PaymentEvent.SubscriptionCreated created)) {
            return;
        }
        if (!processedEvents.isNew(envelope, HANDLER)) {
            return;
        }
        send(UUID.randomUUID(), created.userId(), NotificationTemplates.SUBSCRIPTION_WELCOME,
            Map.of("planCode", created.planCode()), envelope.eventId());
    }

    /**
     * The orchestrated trigger: the saga decided the email is due and named the
     * notification. The claim makes a redelivered command a no-op, and the
     * notification id stays the one the orchestrator is waiting for.
     */
    @Transactional
    public void onSendNotification(EventEnvelope<NotificationCommand> envelope) {
        if (!(envelope.payload() instanceof NotificationCommand.SendNotification command)) {
            return;
        }
        if (!processedEvents.isNew(envelope, COMMAND_HANDLER)) {
            return;
        }
        send(command.notificationId(), command.userId(), command.template(),
            command.data(), envelope.eventId());
    }

    private void send(UUID notificationId, UUID userId, String template,
                      Map<String, String> data, UUID sourceEventId) {
        ContactDirectory.Contact contact = contacts.find(userId).orElseThrow(() ->
            new IllegalStateException("No contact for user " + userId
                + ": UserRegistered has not been projected yet"));

        Map<String, String> templateData = new HashMap<>(data == null ? Map.of() : data);
        templateData.put("displayName", contact.displayName() == null ? "" : contact.displayName());

        NotificationTemplates.EmailContent content = templates.render(template, templateData);
        emailSender.send(contact.email(), content);

        notificationLog.sent(notificationId, userId, template, contact.email(), sourceEventId);
        outbox.append(KafkaTopics.NOTIFICATION_EVENTS, notificationId.toString(),
            new NotificationEvent.NotificationSent(notificationId, userId, template,
                contact.email(), Instant.now()));
    }
}
