package dev.zynema.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What travels on the wire is the payload, so the contracts' job is that a
 * concrete event serialises with its discriminator and deserialises back
 * <strong>when the declared type is its domain interface</strong> — which is
 * exactly what a consumer does ({@code json.value.type=PaymentEvent}).
 *
 * <p>The registry and the producers' type derivation are checked together: a
 * mismatch would be a message that never reaches its consumer.
 */
class EventSerializationTests {

    /**
     * ISO-8601 instants, like Boot's ObjectMapper (and the cache configuration,
     * ADR-0013): numeric timestamps lose nanoseconds on the way back.
     */
    private static final ObjectMapper JSON = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("a payment event round-trips through its domain interface")
    void paymentEventsRoundTrip() throws Exception {
        PaymentEvent.SubscriptionCreated event = new PaymentEvent.SubscriptionCreated(
            subscriptionId, userId, UUID.randomUUID(), "standard", new BigDecimal("9.99"), "USD", Instant.now());

        String json = JSON.writeValueAsString(event);

        assertThat(json).contains("\"eventType\":\"subscription-created\"");
        assertThat(JSON.readValue(json, PaymentEvent.class)).isEqualTo(event);
    }

    @Test
    @DisplayName("every domain interface resolves its own subtypes")
    void everyDomainRoundTrips() throws Exception {
        assertThat(JSON.readValue(
            JSON.writeValueAsString(new PaymentEvent.PaymentSucceeded(
                subscriptionId, userId, new BigDecimal("9.99"), "USD", "card", Instant.now())),
            PaymentEvent.class)).isInstanceOf(PaymentEvent.PaymentSucceeded.class);

        assertThat(JSON.readValue(
            JSON.writeValueAsString(new PlaybackEvent.SessionProgressed(
                UUID.randomUUID(), userId, UUID.randomUUID(), 42, 2400, Instant.now())),
            PlaybackEvent.class)).isInstanceOf(PlaybackEvent.SessionProgressed.class);

        assertThat(JSON.readValue(
            JSON.writeValueAsString(new UserEvent.RoleGranted(userId, "subscriber", Instant.now())),
            UserEvent.class)).isInstanceOf(UserEvent.RoleGranted.class);

        assertThat(JSON.readValue(
            JSON.writeValueAsString(new NotificationEvent.NotificationSent(UUID.randomUUID(), userId,
                "subscription-welcome", "demo@zynema.dev", Instant.now())),
            NotificationEvent.class)).isInstanceOf(NotificationEvent.NotificationSent.class);
    }

    @Test
    @DisplayName("commands carry their own discriminator and round-trip too")
    void commandsRoundTrip() throws Exception {
        UserCommand.GrantRole command = new UserCommand.GrantRole(UUID.randomUUID(), userId, "subscriber");

        String json = JSON.writeValueAsString(command);

        assertThat(json).contains("\"commandType\":\"grant-role\"");
        assertThat(JSON.readValue(json, UserCommand.class)).isEqualTo(command);
    }

    @Test
    @DisplayName("the registry and the derivation agree for every registered message")
    void registryAndDerivationAgree() {
        EventTypes.all().forEach((type, messageClass) -> {
            String derived = type.substring(0, type.indexOf('.')) + "."
                + EventTypes.kebabCase(messageClass.getSimpleName());
            assertThat(derived)
                .as("type derivation for %s", messageClass.getSimpleName())
                .isEqualTo(type);
        });
        assertThat(EventTypes.all()).hasSize(18);
    }

    @Test
    @DisplayName("an unknown type fails loudly instead of yielding a map")
    void unknownTypesFail() {
        assertThatThrownBy(() -> EventTypes.classOf("payment.who-knows"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("payment.who-knows");
    }

    @Test
    @DisplayName("topics have domain prefixes and the DLT suffix is the one Spring uses")
    void topicNamesAreStable() {
        assertThat(KafkaTopics.all()).containsExactly(
            "zynema.payment.events", "zynema.user.events", "zynema.playback.events",
            "zynema.notification.events", "zynema.user.commands", "zynema.notification.commands");
        assertThat(EventTypes.domainOf(KafkaTopics.PAYMENT_EVENTS)).isEqualTo("payment");
        assertThat(EventTypes.domainOf(KafkaTopics.USER_COMMANDS)).isEqualTo("user");
        assertThat(KafkaTopics.DLT_SUFFIX).isEqualTo("-dlt");
    }
}
