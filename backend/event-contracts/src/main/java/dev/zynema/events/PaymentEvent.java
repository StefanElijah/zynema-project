package dev.zynema.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Events published by payment-service on {@link KafkaTopics#PAYMENT_EVENTS}.
 *
 * <p>The envelope's {@code type} identifies the concrete event
 * (see {@link EventTypes}), so a new event type is a registry entry plus a
 * consumer, never a new topic.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PaymentEvent.SubscriptionCreated.class, name = "subscription-created"),
    @JsonSubTypes.Type(value = PaymentEvent.SubscriptionCancelled.class, name = "subscription-cancelled"),
    @JsonSubTypes.Type(value = PaymentEvent.PaymentSucceeded.class, name = "payment-succeeded"),
    @JsonSubTypes.Type(value = PaymentEvent.SubscriptionNotificationFailed.class, name = "subscription-notification-failed")
})
public sealed interface PaymentEvent
    permits PaymentEvent.SubscriptionCreated, PaymentEvent.SubscriptionCancelled,
            PaymentEvent.PaymentSucceeded, PaymentEvent.SubscriptionNotificationFailed {

    UUID subscriptionId();

    UUID userId();

    Instant occurredAt();

    record SubscriptionCreated(
        UUID subscriptionId,
        UUID userId,
        UUID planId,
        String planCode,
        BigDecimal amount,
        String currency,
        Instant occurredAt
    ) implements PaymentEvent {
    }

    record SubscriptionCancelled(
        UUID subscriptionId,
        UUID userId,
        String reason,
        Instant occurredAt
    ) implements PaymentEvent {
    }

    record PaymentSucceeded(
        UUID subscriptionId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        Instant occurredAt
    ) implements PaymentEvent {
    }

    /**
     * The compensation trigger of the choreographed saga: notification-service
     * could not deliver the welcome email, and payment is the only service that
     * can decide what that means for the subscription.
     */
    record SubscriptionNotificationFailed(
        UUID subscriptionId,
        UUID userId,
        String template,
        String reason,
        Instant occurredAt
    ) implements PaymentEvent {
    }
}
