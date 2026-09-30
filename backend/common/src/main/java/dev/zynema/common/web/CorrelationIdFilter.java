package dev.zynema.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every request carries a correlation id.
 *
 * Behavior:
 *  - If the client (usually the API Gateway or the BFF) sent an X-Correlation-Id
 *    header, we reuse it so the whole request graph shares the same id.
 *  - Otherwise we generate one (entry point of the graph).
 *  - The id is exposed in the SLF4J MDC under the key "correlationId" so log
 *    patterns can print it, and echoed back on the response header.
 *
 * This is the Fase-1 minimal piece of distributed tracing; in Fase 9 this is
 * replaced/extended by OpenTelemetry trace context propagation.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
