package dev.zynema.notification.notification;

import dev.zynema.events.NotificationCommand;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/**
 * The templates the service can render, addressed by name.
 *
 * <p>Small on purpose: rendering is a function from a name plus data to a
 * subject and a body. An unknown template is a hard failure, because a wrong
 * email is worse than a loud one — the retry machinery will park it on the
 * dead-letter topic where it can be seen and fixed.
 */
@Component
public class NotificationTemplates {

    /** The name is a contract constant: orchestrators address it by this value. */
    public static final String SUBSCRIPTION_WELCOME = NotificationCommand.SUBSCRIPTION_WELCOME;

    private static final Map<String, Function<Map<String, String>, EmailContent>> TEMPLATES =
        Map.of(SUBSCRIPTION_WELCOME, NotificationTemplates::subscriptionWelcome);

    public EmailContent render(String template, Map<String, String> data) {
        Function<Map<String, String>, EmailContent> renderer = TEMPLATES.get(template);
        if (renderer == null) {
            throw new IllegalArgumentException("Unknown notification template '" + template + "'");
        }
        return renderer.apply(data);
    }

    private static EmailContent subscriptionWelcome(Map<String, String> data) {
        String name = data.getOrDefault("displayName", "there");
        String plan = data.getOrDefault("planCode", "your");
        return new EmailContent(
            "Welcome to Zynema",
            """
            Hi %s,

            Your %s subscription is active and the whole catalogue is open.

            — The Zynema team
            """.formatted(name, plan));
    }

    public record EmailContent(String subject, String body) {
    }
}
