package dev.zynema.common.webflux;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Reactive counterpart of {@code CorrelationIdFilter}.
 *
 * MDC is thread-bound, which does not work on the Reactor event loop, so in
 * WebFlux the id travels in the Reactor Context under the same key. Logging
 * patterns that want it should read from the context (or use a logging
 * framework hook such as reactor-slf4j-mdc, added in Fase 9).
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String CONTEXT_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        final String cid = correlationId;

        // The id must travel downstream in the request, not only back to the
        // client: otherwise every service in the graph generates its own and
        // the response ends up with several conflicting headers.
        ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, cid).build();
        exchange.getResponse().getHeaders().set(HEADER, cid);

        return chain.filter(exchange.mutate().request(request).build())
            .contextWrite(ctx -> ctx.put(CONTEXT_KEY, cid));
    }
}
