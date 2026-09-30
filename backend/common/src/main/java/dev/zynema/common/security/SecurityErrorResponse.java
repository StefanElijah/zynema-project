package dev.zynema.common.security;

import dev.zynema.common.dto.ApiError;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds the {@link ApiError} body for 401/403 responses so every service
 * answers security failures with the same envelope the rest of the API uses.
 */
final class SecurityErrorResponse {

    private SecurityErrorResponse() {
    }

    static ApiError unauthorized(String path, String traceId) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource", path, traceId);
    }

    static ApiError forbidden(String path, String reason, String traceId) {
        return build(HttpStatus.FORBIDDEN, reason == null ? "Access is denied" : reason, path, traceId);
    }

    private static ApiError build(HttpStatus status, String message, String path, String traceId) {
        return new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            path,
            traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId,
            null,
            null
        );
    }
}
