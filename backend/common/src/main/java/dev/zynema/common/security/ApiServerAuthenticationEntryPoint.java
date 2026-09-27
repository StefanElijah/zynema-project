package dev.zynema.common.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.webflux.CorrelationIdWebFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Reactive counterpart of {@link ApiAuthenticationEntryPoint}. */
@RequiredArgsConstructor
public class ApiServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return write(exchange, HttpStatus.UNAUTHORIZED,
            SecurityErrorResponse.unauthorized(exchange.getRequest().getURI().getPath(), correlationId(exchange)));
    }

    static String correlationId(ServerWebExchange exchange) {
        return exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status,
                             dev.zynema.common.dto.ApiError body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            DataBuffer buffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
