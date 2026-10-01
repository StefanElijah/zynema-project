package dev.zynema.common.exception;

import dev.zynema.common.dto.ApiError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reactive handler must produce the same envelope as the servlet one, or
 * the SPA would have to branch on which stack answered.
 */
class ReactiveGlobalExceptionHandlerTest {

    private final ReactiveGlobalExceptionHandler handler = new ReactiveGlobalExceptionHandler();
    private final ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/web/catalog/dune").build());

    @Test
    void notFoundProduces404WithThePath() {
        ResponseEntity<ApiError> response = handler.handleNotFound(
            new ResourceNotFoundException("Movie 42 not found"), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Movie 42 not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/web/catalog/dune");
        assertThat(response.getBody().traceId()).isNotBlank();
    }

    @Test
    void businessAndConflictKeepTheirSemantics() {
        assertThat(handler.handleBusiness(new BusinessRuleException("nope"), exchange).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(handler.handleConflict(new ConflictException("taken"), exchange).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void subscriptionRequiredProduces402WithTheCode() {
        ResponseEntity<ApiError> response = handler.handleSubscriptionRequired(
            new SubscriptionRequiredException("An active subscription is required"), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details()).containsEntry("code", "SUBSCRIPTION_REQUIRED");
    }

    @Test
    void anOpenCircuitProduces503WithTheDependency() {
        ResponseEntity<ApiError> response = handler.handleCircuitOpen(
            CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults("catalog-service")), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().details()).containsEntry("dependency", "catalog-service");
    }

    @Test
    void aDownstream4xxKeepsItsStatusAndMessage() {
        ResponseEntity<ApiError> response = handler.handleDownstream(
            new DownstreamServiceException("payment-service", "payment-service responded 409",
                HttpStatus.CONFLICT, "Subscription already active"), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Subscription already active");
        assertThat(response.getBody().details()).containsEntry("service", "payment-service");
    }

    @Test
    void anUnreachableDependencyBecomes503() {
        ResponseEntity<ApiError> response = handler.handleDownstream(
            new DownstreamServiceException("payment-service", "Could not reach payment-service"), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void anInvalidBodyProduces400WithFieldViolations() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("handle", Payload.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Payload(""), "payload");
        binding.rejectValue("name", "NotBlank", "must not be blank");

        ResponseEntity<ApiError> response = handler.handleValidation(
            new WebExchangeBindException(parameter, binding), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().violations()).hasSize(1);
        assertThat(response.getBody().violations().get(0).field()).isEqualTo("name");
    }

    @Test
    void aFrameworkExceptionKeepsItsStatus() {
        assertThat(handler.handleGeneric(new ResponseStatusException(HttpStatus.NOT_FOUND), exchange)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleGeneric(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), exchange)
            .getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void anUnknownFailureIsA500WithoutDetails() {
        ResponseEntity<ApiError> response = handler.handleGeneric(new IllegalStateException("boom"), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
    }

    record Payload(String name) {
    }

    static class Fixture {
        void handle(Payload payload) {
        }
    }
}
