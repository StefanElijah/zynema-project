package dev.zynema.payment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * What the subscription read exposes when a notification failed permanently:
 * the client can tell the user, and support can see the reason without
 * reading Kafka.
 */
public record NotificationFailureDto(
    UUID notificationId,
    String template,
    String reason,
    Instant occurredAt
) {
}
