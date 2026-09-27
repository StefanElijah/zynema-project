package dev.zynema.common.exception;

/**
 * Thrown when the request conflicts with the current state of the resource:
 * a duplicate subscription, an idempotency key reused with another payload, a
 * resource that already exists.
 *
 * <p>It exists separately from {@link BusinessRuleException} because the HTTP
 * semantics differ: a conflict is a 409 (the client may retry after resolving
 * the state) while a violated business rule is a 422 (the request is understood
 * and cannot be applied as-is).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
