package dev.zynema.common.feign;

import dev.zynema.common.web.CorrelationIdFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * Propagates the caller's context on every outbound Feign call.
 *
 * <p>Three things travel:
 * <ol>
 *   <li><strong>Authorization</strong> — the end user's bearer token, so the
 *       downstream service applies <em>its own</em> rules to the same identity
 *       (on-behalf-of semantics, ADR-0019). Without it, service-to-service calls
 *       would have to bypass authorization, which is how systems end up with
 *       unprotected internal endpoints.</li>
 *   <li><strong>X-Correlation-Id</strong> — so one id ties together every log
 *       line of the whole request graph.</li>
 *   <li><strong>W3C trace context</strong> (traceparent/tracestate) — so the
 *       downstream span becomes a child of the current span and the trace is
 *       continuous. Feign is not instrumented by Micrometer Tracing, so this
 *       is done explicitly.</li>
 * </ol>
 *
 * <p>Tracing is optional: services without the bridge simply skip that part.
 */
@Slf4j
@RequiredArgsConstructor
public class FeignRequestInterceptor implements RequestInterceptor {

    private final ObjectProvider<Tracer> tracerProvider;
    private final ObjectProvider<Propagator> propagatorProvider;

    @Override
    public void apply(RequestTemplate template) {
        relayAuthorization(template);
        relayCorrelationId(template);
        relayTraceContext(template);
    }

    private void relayAuthorization(RequestTemplate template) {
        String authorization = currentHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && !template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
            template.header(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    private void relayCorrelationId(RequestTemplate template) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (!StringUtils.hasText(correlationId) && currentHeader(CorrelationIdFilter.HEADER) != null) {
            correlationId = currentHeader(CorrelationIdFilter.HEADER);
        }
        if (StringUtils.hasText(correlationId) && !template.headers().containsKey(CorrelationIdFilter.HEADER)) {
            template.header(CorrelationIdFilter.HEADER, correlationId);
        }
    }

    private void relayTraceContext(RequestTemplate template) {
        Tracer tracer = tracerProvider.getIfAvailable();
        Propagator propagator = propagatorProvider.getIfAvailable();
        if (tracer == null || propagator == null) {
            return;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return;
        }
        Map<String, String> headers = new HashMap<>();
        propagator.inject(currentSpan.context(), headers, Map::put);
        headers.forEach((name, value) -> {
            if (!template.headers().containsKey(name)) {
                template.header(name, value);
            }
        });
    }

    private String currentHeader(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getHeader(name);
        }
        return null;
    }
}
