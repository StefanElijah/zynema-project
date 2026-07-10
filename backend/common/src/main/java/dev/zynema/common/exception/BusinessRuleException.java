package dev.zynema.common.exception;

/** Thrown when a business invariant is violated (HTTP 422). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) { super(message); }
}
