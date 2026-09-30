package dev.zynema.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One run of the orchestrated onboarding (ADR-0029).
 *
 * <p>The id is minted when the saga starts and travels in every command and
 * reply, which is what lets the replies be matched without guessing. The row is
 * the single place where the flow's progress lives — the contrast with the
 * choreographed version, where the progress is only in the events.
 */
@Entity
@Table(name = "subscription_onboarding_sagas")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SubscriptionOnboardingSaga {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "subscription_id", nullable = false, unique = true)
    private UUID subscriptionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_code", length = 40)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OnboardingSagaState state;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
