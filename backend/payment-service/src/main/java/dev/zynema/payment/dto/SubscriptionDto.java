package dev.zynema.payment.dto;

import dev.zynema.payment.domain.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionDto(
    UUID id,
    UUID userId,
    PlanDto plan,
    SubscriptionStatus status,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    boolean cancelAtPeriodEnd,
    Instant createdAt,
    Instant canceledAt,
    /**
     * The latest permanently-failed notification, if any. Null when every
     * delivery succeeded — the normal case.
     */
    NotificationFailureDto notificationFailure
) {

    public SubscriptionDto withNotificationFailure(NotificationFailureDto failure) {
        return new SubscriptionDto(id, userId, plan, status, currentPeriodStart, currentPeriodEnd,
            cancelAtPeriodEnd, createdAt, canceledAt, failure);
    }
}
