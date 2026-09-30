package dev.zynema.payment.domain;

/**
 * Where an onboarding saga is (ADR-0029).
 *
 * <p>{@code COMPLETED} means every step that could succeed did; a
 * {@code failure_reason} on a completed saga records a step the platform
 * decided not to compensate for (a failed welcome email flags the subscription
 * instead of reversing it). {@code COMPENSATED} means a step failed and the
 * saga ran its explicit compensating action.
 */
public enum OnboardingSagaState {
    AWAITING_ROLE,
    AWAITING_NOTIFICATION,
    COMPLETED,
    COMPENSATED
}
