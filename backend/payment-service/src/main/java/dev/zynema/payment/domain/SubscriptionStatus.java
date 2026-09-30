package dev.zynema.payment.domain;

/**
 * Lifecycle of a subscription.
 *
 * <p>{@code CANCELED} is reached when the paid period ends without renewal
 * (driven by an event in a later phase); a cancellation requested by the user
 * only sets {@code cancel_at_period_end} so the account keeps its benefits
 * until the period it already paid for is over.
 */
public enum SubscriptionStatus {
    ACTIVE,
    CANCELED,
    PAST_DUE
}
