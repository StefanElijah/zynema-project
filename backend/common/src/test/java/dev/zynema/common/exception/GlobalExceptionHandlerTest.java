package dev.zynema.common.exception;

import dev.zynema.common.dto.ApiError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the shared exception handler produces the stable ApiError
 * envelope that the BFF and the frontend depend on.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(new FaultyController())
            .setControllerAdvice(handler)
            .setValidator(new LocalValidatorFactoryBean())
            .build();
    }

    @Test
    void resourceNotFoundProducesApiErrorEnvelope() throws Exception {
        mockMvc.perform(get("/test/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("Movie 42 not found"))
            .andExpect(jsonPath("$.path").value("/test/missing"))
            .andExpect(jsonPath("$.timestamp").isNotEmpty())
            .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void businessRuleViolationProduces422() throws Exception {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.message").value("Subscription already active"));
    }

    @Test
    void unhandledExceptionProduces500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void conflictProduces409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("Profile already exists"));
    }

    @Test
    void subscriptionRequiredProduces402WithTheCode() throws Exception {
        mockMvc.perform(get("/test/subscription"))
            .andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.status").value(402))
            .andExpect(jsonPath("$.details.code").value("SUBSCRIPTION_REQUIRED"));
    }

    @Test
    void anOpenCircuitProduces503WithTheDependency() throws Exception {
        mockMvc.perform(get("/test/circuit"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("A dependency is temporarily unavailable (payment-service)"))
            .andExpect(jsonPath("$.details.dependency").value("payment-service"));
    }

    @Test
    void aDownstream4xxKeepsItsStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test/downstream-conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.message").value("Subscription already active"))
            .andExpect(jsonPath("$.details.service").value("payment-service"));
    }

    @Test
    void anUnreachableDependencyBecomes503() throws Exception {
        mockMvc.perform(get("/test/downstream-down"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.message").value("Could not reach payment-service"))
            .andExpect(jsonPath("$.details.service").value("payment-service"));
    }

    @Test
    void anInvalidBodyProduces400WithFieldViolations() throws Exception {
        mockMvc.perform(post("/test/valid")
                .contentType("application/json")
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.violations[0].field").value("name"));
    }

    @Test
    void aMalformedBodyProduces400() throws Exception {
        mockMvc.perform(post("/test/read")
                .contentType("application/json")
                .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void aMissingParameterProduces400NamingIt() throws Exception {
        mockMvc.perform(get("/test/param"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Required parameter 'q' is missing"));
    }

    @Test
    void aBadParameterTypeProduces400() throws Exception {
        mockMvc.perform(get("/test/type").param("size", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Parameter 'size' has an invalid value: abc"));
    }

    @Test
    void aWrongMethodProduces405() throws Exception {
        mockMvc.perform(post("/test/missing"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void aFrameworkExceptionKeepsIts4xx() throws Exception {
        mockMvc.perform(get("/test/teapot"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not Found"));
    }

    @Test
    void constraintViolationsOnParametersProduce400() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<Payload>> violations = validator.validate(new Payload(""));

        ResponseEntity<ApiError> response = handler.handleConstraintViolation(
            new ConstraintViolationException(violations),
            new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().violations()).hasSize(1);
    }

    record Payload(@NotBlank String name) {
    }

    @RestController
    static class FaultyController {

        @GetMapping("/test/missing")
        void missing() {
            throw new ResourceNotFoundException("Movie 42 not found");
        }

        @GetMapping("/test/business")
        void business() {
            throw new BusinessRuleException("Subscription already active");
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret internals that must not leak");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException("Profile already exists");
        }

        @GetMapping("/test/subscription")
        void subscription() {
            throw new SubscriptionRequiredException("An active subscription is required");
        }

        @GetMapping("/test/circuit")
        void circuit() {
            throw CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults("payment-service"));
        }

        @GetMapping("/test/downstream-conflict")
        void downstreamConflict() {
            throw new DownstreamServiceException("payment-service", "payment-service responded 409",
                HttpStatus.CONFLICT, "Subscription already active");
        }

        @GetMapping("/test/downstream-down")
        void downstreamDown() {
            throw new DownstreamServiceException("payment-service", "Could not reach payment-service");
        }

        @PostMapping("/test/valid")
        void valid(@RequestBody @jakarta.validation.Valid Payload payload) {
        }

        @PostMapping("/test/read")
        void read(@RequestBody java.util.Map<String, Object> body) {
        }

        @GetMapping("/test/param")
        void param(@RequestParam String q) {
        }

        @GetMapping("/test/type")
        void type(@RequestParam int size) {
        }

        @GetMapping("/test/teapot")
        void teapot() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
