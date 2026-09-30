package dev.zynema.common.exception;

/**
 * The caller is authenticated but not entitled to the resource: no active
 * plan, or a plan that lapsed.
 *
 * <p>Mapped to {@code 402 Payment Required}. The status is the point: the
 * request is well-formed and the identity is known — what is missing is money,
 * and the client should show a paywall instead of a login screen or a generic
 * error. Enforcement lives where the resource is served, never in the BFF: the
 * BFF only reports what it learned from an entitlement check.
 */
public class SubscriptionRequiredException extends RuntimeException {

    public SubscriptionRequiredException(String message) {
        super(message);
    }
}
