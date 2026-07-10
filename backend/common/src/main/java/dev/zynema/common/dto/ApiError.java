package dev.zynema.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Standard error envelope returned by all REST endpoints.
 * Shape is stable across services so the BFF can pass it through verbatim.
 */
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String traceId,
    List<FieldViolation> violations,
    Map<String, Object> details
) {
    public record FieldViolation(String field, Object rejectedValue, String message) {}
}
