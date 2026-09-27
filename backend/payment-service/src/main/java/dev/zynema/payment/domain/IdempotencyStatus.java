package dev.zynema.payment.domain;

/**
 * Reservation state of an idempotency key.
 *
 * <p>{@code IN_PROGRESS} means a request with this key is being processed right
 * now; a concurrent duplicate must not start the work again.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
