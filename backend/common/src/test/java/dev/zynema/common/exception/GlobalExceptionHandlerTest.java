package dev.zynema.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the shared exception handler produces the stable ApiError
 * envelope that the BFF and the frontend depend on.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FaultyController())
            .setControllerAdvice(new GlobalExceptionHandler())
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
    }
}
