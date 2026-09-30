package dev.zynema.events;

import java.util.HashMap;
import java.util.Map;

/**
 * The mapping between an envelope's {@code type} and the concrete event or
 * command class.
 *
 * <p>This exists instead of Jackson's polymorphic typing, for two reasons:
 * the payload field of the envelope is generic ({@code T}), so an annotation on
 * the interface would never be consulted when the producer serialises a
 * concrete record; and a hand-written map is a contract you can read, test and
 * version. A typo is a failing test, not a message that silently deserialises
 * into a map.
 *
 * <p>{@code type} values are derived from the class name (kebab case, domain
 * prefix) and never change when a class is renamed — the map, not the class
 * name, is what consumers match against.
 */
public final class EventTypes {

    private static final Map<String, Class<?>> BY_TYPE = new HashMap<>();

    static {
        register("payment", PaymentEvent.SubscriptionCreated.class);
        register("payment", PaymentEvent.SubscriptionCancelled.class);
        register("payment", PaymentEvent.PaymentSucceeded.class);
        register("payment", PaymentEvent.SubscriptionNotificationFailed.class);
        register("user", UserEvent.UserRegistered.class);
        register("user", UserEvent.RoleGranted.class);
        register("user", UserEvent.RoleRevoked.class);
        register("user", UserEvent.RoleChangeFailed.class);
        register("playback", PlaybackEvent.SessionStarted.class);
        register("playback", PlaybackEvent.SessionPaused.class);
        register("playback", PlaybackEvent.SessionResumed.class);
        register("playback", PlaybackEvent.SessionProgressed.class);
        register("playback", PlaybackEvent.SessionStopped.class);
        register("notification", NotificationEvent.NotificationSent.class);
        register("notification", NotificationEvent.NotificationFailed.class);
        register("user", UserCommand.GrantRole.class);
        register("user", UserCommand.RevokeRole.class);
        register("notification", NotificationCommand.SendNotification.class);
    }

    private EventTypes() {
    }

    private static void register(String domain, Class<?> type) {
        BY_TYPE.put(forPayload(domain, type.getSimpleName()), type);
    }

    /** e.g. {@code payment} + {@code SubscriptionCreated} → {@code payment.subscription-created}. */
    public static String forPayload(String domain, Object payload) {
        return forPayload(domain, payload.getClass().getSimpleName());
    }

    public static String forPayload(String domain, String className) {
        return domain + "." + kebabCase(className);
    }

    /** The domain part of a topic: {@code zynema.payment.events} → {@code payment}. */
    public static String domainOf(String topic) {
        return topic.replaceFirst("^zynema\\.", "").replaceFirst("\\.(events|commands)$", "");
    }

    public static Class<?> classOf(String type) {
        Class<?> resolved = BY_TYPE.get(type);
        if (resolved == null) {
            throw new IllegalArgumentException("Unknown message type '" + type + "': add it to EventTypes");
        }
        return resolved;
    }

    /** Every registered type, for tests and tooling. */
    public static Map<String, Class<?>> all() {
        return Map.copyOf(BY_TYPE);
    }

    static String kebabCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
