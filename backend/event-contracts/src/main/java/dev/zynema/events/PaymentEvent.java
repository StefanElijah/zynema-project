package dev.zynema.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Events published by payment-service.
 * Naming: zynema.payment.subscription.&lt;verb&gt;
 */
public sealed interface PaymentEvent
    permits PaymentEvent.SubscriptionCreated, PaymentEvent.SubscriptionCancelled, PaymentEvent.PaymentSucceeded {

    UUID subscriptionId();
    UUID userId();
    Instant occurredAt();

    /** zynema.payment.subscription.created */
    record SubscriptionCreated(
        UUID subscriptionId,
        UUID userId,
        UUID planId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
    ) implements PaymentEvent {}

    /** zynema.payment.subscription.cancelled */
    record SubscriptionCancelled(
        UUID subscriptionId,
        UUID userId,
        String reason,
        Instant occurredAt
    ) implements PaymentEvent {}

    /** zynema.payment.payment.succeeded */
    record PaymentSucceeded(
        UUID subscriptionId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        Instant occurredAt
    ) implements PaymentEvent {}
}
