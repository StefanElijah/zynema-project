package dev.zynema.common.exception;

import dev.zynema.common.dto.ApiError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reactive counterpart of {@link GlobalExceptionHandler} for WebFlux services
 * (bff-service, api-gateway). Produces the exact same {@link ApiError} envelope
 * so the frontend cannot tell which stack served the error.
 */
@Slf4j
@RestControllerAdvice
public class ReactiveGlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), exchange, null, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessRuleException ex, ServerWebExchange exchange) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), exchange, null, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, ServerWebExchange exchange) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), exchange, null, null);
    }

    @ExceptionHandler(SubscriptionRequiredException.class)
    public ResponseEntity<ApiError> handleSubscriptionRequired(SubscriptionRequiredException ex, ServerWebExchange exchange) {
        return build(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), exchange, null,
            java.util.Map.of("code", "SUBSCRIPTION_REQUIRED"));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> handleCircuitOpen(CallNotPermittedException ex, ServerWebExchange exchange) {
        log.warn("Circuit breaker '{}' is open, rejecting the request", ex.getCausingCircuitBreakerName());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
            "A dependency is temporarily unavailable (%s)".formatted(ex.getCausingCircuitBreakerName()),
            exchange, null, java.util.Map.of("dependency", ex.getCausingCircuitBreakerName()));
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ApiError> handleDownstream(DownstreamServiceException ex, ServerWebExchange exchange) {
        HttpStatus status = ex.resolveStatus();
        String message = ex.getDownstreamMessage() != null ? ex.getDownstreamMessage() : ex.getMessage();
        if (status.is5xxServerError()) {
            log.warn("Returning {} for a downstream failure on {}: {}", status.value(), ex.getServiceName(), ex.getMessage());
        }
        return build(status, message, exchange, null, java.util.Map.of("service", ex.getServiceName()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> handleValidation(WebExchangeBindException ex, ServerWebExchange exchange) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()))
            .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", exchange, violations, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, ServerWebExchange exchange) {
        // Framework exceptions that already carry a status must keep it.
        // Gateway routing failures (no instance available) surface as
        // ErrorResponse with 503; collapsing them into 500 would tell the
        // client "we are broken" when the truth is "that dependency is down".
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status != null) {
                if (status.is5xxServerError()) {
                    log.warn("Upstream failure mapped to {}: {}", status, ex.getMessage());
                }
                return build(status, status.getReasonPhrase(), exchange, null, null);
            }
        }
        log.error("Unhandled exception in reactive pipeline", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", exchange, null, null);
    }

    private ResponseEntity<ApiError> build(
        HttpStatus status, String message, ServerWebExchange exchange,
        List<ApiError.FieldViolation> violations, java.util.Map<String, Object> details
    ) {
        ApiError body = new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            exchange.getRequest().getPath().value(),
            UUID.randomUUID().toString(),
            violations,
            details
        );
        return ResponseEntity.status(status).body(body);
    }
}
