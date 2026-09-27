package dev.zynema.payment.domain;

/** Lifecycle of a payment attempt. */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED
}
