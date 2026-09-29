package dev.zynema.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification that notification-service could not deliver, recorded against
 * the subscription it belonged to (ADR-0007).
 *
 * <p>The id comes from the producer — it is the notification's identity — and
 * makes the write idempotent on its own, on top of the consumer's claim. The
 * subscription is not touched: compensating a failed email by revoking a paid
 * subscription would take money the user is still entitled to.
 */
@Entity
@Table(name = "subscription_notification_failures")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class NotificationFailure {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String template;

    @Column(length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
}
