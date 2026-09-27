package dev.zynema.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.dto.ApiError;
import dev.zynema.gateway.config.RateLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Distributed rate limiting at the edge.
 *
 * <p>The limiter is a Redis token bucket ({@link RedisRateLimiter}, an atomic
 * Lua script shared by every gateway instance). This filter adds the two things
 * the stock filter does not:
 *
 * <ul>
 *   <li>a <strong>429 with the platform's {@code ApiError} envelope</strong> and
 *       {@code Retry-After}, instead of an empty body;</li>
 *   <li>a stable rate-limit identity: the authenticated subject when there is a
 *       token, the client address otherwise. Keying anonymous traffic by IP is
 *       what protects the public catalogue endpoints.</li>
 * </ul>
 *
 * <p>It runs after the security filters (gateway global filters execute inside
 * the handler, once authentication has populated the Reactor context), so the
 * principal is available.
 */
@Slf4j
@Component
public class ApiRateLimitFilter implements GlobalFilter, Ordered {

    /** Order before routing filters, after the security chain. */
    public static final int ORDER = 0;

    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public ApiRateLimitFilter(RedisRateLimiter rateLimiter,
                              RateLimitProperties properties,
                              ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }
        if (!properties.routes().containsKey(route.getId())) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
            .map(principal -> "user:" + principal.getName())
            .defaultIfEmpty("ip:" + clientAddress(exchange))
            .flatMap(key -> rateLimiter.isAllowed(route.getId(), key)
                .flatMap(decision -> {
                    decision.getHeaders().forEach((name, value) ->
                        exchange.getResponse().getHeaders().add(name, value));
                    if (decision.isAllowed()) {
                        return chain.filter(exchange);
                    }
                    return reject(exchange, route.getId());
                }));
    }

    private Mono<Void> reject(ServerWebExchange exchange, String routeId) {
        long retryAfterSeconds = retryAfterSeconds(routeId);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));

        ApiError body = new ApiError(
            Instant.now(),
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
            "Too many requests, retry in %d second(s)".formatted(retryAfterSeconds),
            exchange.getRequest().getURI().getPath(),
            exchange.getResponse().getHeaders().getFirst("X-Correlation-Id") == null
                ? UUID.randomUUID().toString()
                : exchange.getResponse().getHeaders().getFirst("X-Correlation-Id"),
            null,
            Map.of("route", routeId, "retryAfterSeconds", retryAfterSeconds)
        );
        log.warn("Rate limit exceeded on route '{}' for {}", routeId, exchange.getRequest().getURI().getPath());
        DataBuffer buffer = response.bufferFactory().wrap(toJson(body));
        return response.writeWith(Mono.just(buffer));
    }

    private long retryAfterSeconds(String routeId) {
        RateLimitProperties.RouteLimit limit = properties.routes().get(routeId);
        int replenishRate = limit == null ? 1 : Math.max(1, limit.replenishRate());
        return Math.max(1, (long) Math.ceil(1.0 / replenishRate));
    }

    /**
     * Client address for anonymous traffic. Behind a proxy the socket address is
     * the proxy, so the first {@code X-Forwarded-For} entry is used when
     * present.
     */
    private String clientAddress(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() == null
            ? "unknown"
            : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    /** Uses the application mapper, so the body matches every other error. */
    private byte[] toJson(ApiError error) {
        try {
            return objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the rate limit error", ex);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
