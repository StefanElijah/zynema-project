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
    Instant canceledAt
) {
}
