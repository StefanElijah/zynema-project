package dev.zynema.events;

/**
 * The canonical topic names.
 *
 * <p>One topic per aggregate, not per event type: the roadmap's four topics,
 * ordered per aggregate by using the aggregate id as the key, one dead-letter
 * destination to operate per aggregate, and room for new event types without a
 * new topic. The event type travels in the envelope.
 *
 * <p>Commands get their own topics and are deliberately separate from events:
 * an event says "this happened" and can have many readers; a command says "do
 * this" and must have exactly one logical consumer. Mixing them in one topic
 * makes both statements untrue.
 *
 * <p>Named without a version segment on purpose: versions live in the payload
 * schema (JSON Schema, compatibility-checked by the registry), and renaming a
 * topic would be a new topic rather than a new version of an old one.
 */
public final class KafkaTopics {

    // Events (one per aggregate)
    public static final String PAYMENT_EVENTS = "zynema.payment.events";
    public static final String USER_EVENTS = "zynema.user.events";
    public static final String PLAYBACK_EVENTS = "zynema.playback.events";
    public static final String NOTIFICATION_EVENTS = "zynema.notification.events";

    // Commands (orchestrated saga)
    public static final String USER_COMMANDS = "zynema.user.commands";
    public static final String NOTIFICATION_COMMANDS = "zynema.notification.commands";

    /** Suffix Spring Kafka's {@code @RetryableTopic} uses for exhausted messages. */
    public static final String DLT_SUFFIX = "-dlt";

    private KafkaTopics() {
    }

    /** The topics {@code kafka-init} must create, in the order it prints them. */
    public static String[] all() {
        return new String[]{
            PAYMENT_EVENTS, USER_EVENTS, PLAYBACK_EVENTS, NOTIFICATION_EVENTS,
            USER_COMMANDS, NOTIFICATION_COMMANDS
        };
    }
}
