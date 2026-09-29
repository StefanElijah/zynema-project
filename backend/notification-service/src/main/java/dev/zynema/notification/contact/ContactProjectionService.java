package dev.zynema.notification.contact;

import dev.zynema.common.messaging.ProcessedEventStore;
import dev.zynema.events.EventEnvelope;
import dev.zynema.events.UserEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes the user domain to keep {@link ContactDirectory} current.
 *
 * <p>The claim ({@code ProcessedEventStore}) and the upsert share one
 * transaction: a redelivery is a no-op, and a rollback leaves the event
 * unclaimed so the retry can run (ADR-0026).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactProjectionService {

    public static final String HANDLER = "user-registered-contact";

    private final ProcessedEventStore processedEvents;
    private final ContactDirectory contacts;

    @Transactional
    public void onUserRegistered(EventEnvelope<UserEvent> envelope) {
        if (!(envelope.payload() instanceof UserEvent.UserRegistered registered)) {
            return;
        }
        if (!processedEvents.isNew(envelope, HANDLER)) {
            return;
        }
        contacts.upsert(registered.userId(), registered.email(), registered.displayName());
        log.info("Contact projection updated for user {}", registered.userId());
    }
}
