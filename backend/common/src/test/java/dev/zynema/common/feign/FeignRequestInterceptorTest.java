package dev.zynema.common.feign;

import dev.zynema.common.web.CorrelationIdFilter;
import feign.RequestTemplate;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeignRequestInterceptorTest {

    private final ObjectProvider<Tracer> noTracer = providerReturning(null);
    private final ObjectProvider<Propagator> noPropagator = providerReturning(null);

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    @DisplayName("relays the caller's bearer token so the downstream authorizes the same identity")
    void relaysAuthorization() {
        givenCurrentRequest("Bearer token-123", null);

        RequestTemplate template = apply(new FeignRequestInterceptor(noTracer, noPropagator));

        assertThat(header(template, HttpHeaders.AUTHORIZATION)).containsExactly("Bearer token-123");
    }

    @Test
    @DisplayName("relays the correlation id from the MDC")
    void relaysCorrelationId() {
        givenCurrentRequest(null, null);
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-42");

        RequestTemplate template = apply(new FeignRequestInterceptor(noTracer, noPropagator));

        assertThat(header(template, CorrelationIdFilter.HEADER)).containsExactly("corr-42");
    }

    @Test
    @DisplayName("falls back to the incoming header when the MDC is empty")
    void correlationIdFallsBackToHeader() {
        givenCurrentRequest(null, "corr-from-header");

        RequestTemplate template = apply(new FeignRequestInterceptor(noTracer, noPropagator));

        assertThat(header(template, CorrelationIdFilter.HEADER)).containsExactly("corr-from-header");
    }

    @Test
    @DisplayName("injects the W3C trace context of the current span")
    void injectsTraceContext() {
        givenCurrentRequest("Bearer token-123", "corr-42");
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(mock(TraceContext.class));
        Propagator propagator = mock(Propagator.class);
        doAnswer(invocation -> {
            java.util.Map<String, String> carrier = invocation.getArgument(1);
            carrier.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
            return null;
        }).when(propagator).inject(any(), anyMap(), any());

        RequestTemplate template = apply(new FeignRequestInterceptor(
            providerReturning(tracer), providerReturning(propagator)));

        assertThat(header(template, "traceparent"))
            .containsExactly("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
    }

    @Test
    @DisplayName("does nothing (and does not fail) without a request context or tracing")
    void worksWithoutContextOrTracing() {
        RequestTemplate template = apply(new FeignRequestInterceptor(noTracer, noPropagator));

        assertThat(template.headers()).isEmpty();
    }

    @Test
    @DisplayName("never overwrites headers a client set explicitly")
    void doesNotOverwriteExplicitHeaders() {
        givenCurrentRequest("Bearer token-123", null);
        RequestTemplate template = new RequestTemplate();
        template.header(HttpHeaders.AUTHORIZATION, "Bearer explicit");

        new FeignRequestInterceptor(noTracer, noPropagator).apply(template);

        assertThat(header(template, HttpHeaders.AUTHORIZATION)).containsExactly("Bearer explicit");
    }

    // ────────────────────────────── helpers ────────────────────────────

    private RequestTemplate apply(FeignRequestInterceptor interceptor) {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        return template;
    }

    private void givenCurrentRequest(String authorization, String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (correlationId != null) {
            request.addHeader(CorrelationIdFilter.HEADER, correlationId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private Collection<String> header(RequestTemplate template, String name) {
        Collection<String> values = template.headers().get(name);
        return values == null ? java.util.List.of() : values;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerReturning(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
