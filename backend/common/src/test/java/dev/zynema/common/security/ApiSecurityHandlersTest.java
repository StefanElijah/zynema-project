package dev.zynema.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 401/403 handlers must answer with the same ApiError envelope the rest of
 * the API uses, not with the servlet container's default error page.
 *
 * <p>The mapper mirrors the application's (Boot registers JavaTimeModule), so
 * serialising the {@code Instant} timestamp is part of what is verified here.
 */
class ApiSecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("entry point responds 401 with an ApiError body")
    void entryPointWritesApiError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint(objectMapper)
            .commence(request, response, new BadCredentialsException("nope"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);

        var body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("message").asText()).contains("Authentication is required");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/users/me");
        assertThat(body.get("traceId").asText()).isNotBlank();
    }

    @Test
    @DisplayName("access denied handler responds 403 with an ApiError body")
    void accessDeniedHandlerWritesApiError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/catalog/admin/contents");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler(objectMapper)
            .handle(request, response, new AccessDeniedException("Access Denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        var body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/catalog/admin/contents");
    }
}
