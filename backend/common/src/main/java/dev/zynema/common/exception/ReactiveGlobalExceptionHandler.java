package dev.zynema.common.exception;

import dev.zynema.common.dto.ApiError;
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
