package dev.zynema.common.exception;

import dev.zynema.common.dto.ApiError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler shared by all services.
 * Produces a consistent {@link ApiError} envelope so the BFF and frontend
 * can parse errors uniformly.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessRuleException ex, WebRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null, null);
    }

    /**
     * The circuit breaker for a dependency is open: the call was never made.
     * It is a 503 because the service is fine and the dependency is not.
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiError> handleCircuitOpen(CallNotPermittedException ex, WebRequest request) {
        log.warn("Circuit breaker '{}' is open, rejecting the request", ex.getCausingCircuitBreakerName());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
            "A dependency is temporarily unavailable (%s)".formatted(ex.getCausingCircuitBreakerName()),
            request, null, Map.of("dependency", ex.getCausingCircuitBreakerName()));
    }

    /**
     * A call to another service failed. The downstream 4xx (conflict, forbidden,
     * not found) is preserved; anything else becomes 503, because the fault is
     * upstream of this service.
     */
    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ApiError> handleDownstream(DownstreamServiceException ex, WebRequest request) {
        HttpStatus status = ex.resolveStatus();
        String message = ex.getDownstreamMessage() != null ? ex.getDownstreamMessage() : ex.getMessage();
        if (status.is5xxServerError()) {
            log.warn("Returning {} for a downstream failure on {}: {}", status.value(), ex.getServiceName(), ex.getMessage());
        }
        return build(status, message, request, null, Map.of("service", ex.getServiceName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()))
            .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations, null);
    }

    /**
     * Bean-validation failures on method parameters (e.g. {@code @Min} on a
     * {@code @RequestParam} of a {@code @Validated} controller).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
            .map(cv -> new ApiError.FieldViolation(
                cv.getPropertyPath().toString(), cv.getInvalidValue(), cv.getMessage()))
            .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, violations, null);
    }

    /** A path/query parameter could not be converted to the expected type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        String message = "Parameter '%s' has an invalid value: %s".formatted(ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST, message, request, null, null);
    }

    /** Malformed or unreadable JSON body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request, null, null);
    }

    /** A required query parameter was not supplied. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST,
            "Required parameter '%s' is missing".formatted(ex.getParameterName()), request, null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, WebRequest request) {
        // Framework exceptions that already carry a 4xx status (e.g.
        // NoResourceFoundException for unmapped URLs, ResponseStatusException)
        // must not become 500s. They implement the ErrorResponse interface,
        // which lives in spring-web so both servlet and reactive stacks see it.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode code = errorResponse.getStatusCode();
            HttpStatus status = HttpStatus.resolve(code.value());
            if (status != null && status.is4xxClientError()) {
                return build(status, status.getReasonPhrase(), request, null, null);
            }
        }
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request, null, null);
    }

    private ResponseEntity<ApiError> build(
        HttpStatus status, String message, WebRequest request,
        List<ApiError.FieldViolation> violations, java.util.Map<String, Object> details
    ) {
        ApiError body = new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getDescription(false).replace("uri=", ""),
            UUID.randomUUID().toString(),
            violations,
            details
        );
        return ResponseEntity.status(status).body(body);
    }
}
