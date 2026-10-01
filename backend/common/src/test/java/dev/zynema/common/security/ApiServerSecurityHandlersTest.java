package dev.zynema.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.webflux.CorrelationIdWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The reactive handlers must answer the same ApiError envelope as the servlet
 * ones: the SPA cannot tell which stack rejected it.
 */
class ApiServerSecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void anUnauthenticatedRequestGetsThe401EnvelopeWithTheCorrelationId() {
        ApiServerAuthenticationEntryPoint entryPoint =
            new ApiServerAuthenticationEntryPoint(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/web/account").build());
        exchange.getResponse().getHeaders().set(CorrelationIdWebFilter.HEADER, "corr-9");

        entryPoint.commence(exchange, new InsufficientAuthenticationException("no token")).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
            .contains("application/json");
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body)
            .contains("\"status\":401")
            .contains("\"error\":\"Unauthorized\"")
            .contains("Authentication is required")
            .contains("/api/v1/web/account")
            .contains("corr-9");
    }

    @Test
    void aForbiddenRequestGetsThe403EnvelopeWithTheReason() {
        ApiServerAccessDeniedHandler handler = new ApiServerAccessDeniedHandler(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/catalog/admin/contents").build());

        handler.handle(exchange, new AccessDeniedException("role required")).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body)
            .contains("\"status\":403")
            .contains("\"error\":\"Forbidden\"")
            .contains("role required");
    }

    @Test
    void aForbiddenRequestWithoutAReasonUsesTheDefaultMessage() {
        ApiServerAccessDeniedHandler handler = new ApiServerAccessDeniedHandler(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/users/me").build());

        handler.handle(exchange, new AccessDeniedException(null)).block();

        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Access is denied");
    }

    @Test
    void aMapperThatCannotWriteFailsThePipeline() throws Exception {
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsBytes(any()))
            .thenThrow(new JsonProcessingException("boom") {
            });

        // Reactor wraps checked exceptions, so the cause is what proves the
        // mapper failure was the one that surfaced.
        MockServerWebExchange deniedExchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/x").build());
        assertThatThrownBy(() -> new ApiServerAccessDeniedHandler(broken)
            .handle(deniedExchange, new AccessDeniedException("no")).block())
            .hasRootCauseInstanceOf(JsonProcessingException.class);

        MockServerWebExchange anonymousExchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/x").build());
        assertThatThrownBy(() -> new ApiServerAuthenticationEntryPoint(broken)
            .commence(anonymousExchange, new InsufficientAuthenticationException("no")).block())
            .hasRootCauseInstanceOf(JsonProcessingException.class);
    }
}
