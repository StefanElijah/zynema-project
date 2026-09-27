package dev.zynema.common.webflux;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    @DisplayName("reuses the incoming id and passes it downstream in the request")
    void propagatesIncomingId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/catalog/movies").header(CorrelationIdWebFilter.HEADER, "abc-123"));
        AtomicReference<String> seenByDownstream = new AtomicReference<>();

        WebFilterChain chain = downstream -> {
            seenByDownstream.set(downstream.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER));
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();

        assertThat(seenByDownstream.get()).isEqualTo("abc-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER)).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("generates an id when the caller did not send one and still forwards it")
    void generatesIdWhenMissing() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me"));
        AtomicReference<String> seenByDownstream = new AtomicReference<>();

        WebFilterChain chain = downstream -> {
            seenByDownstream.set(downstream.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER));
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();

        assertThat(seenByDownstream.get()).isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER))
            .isEqualTo(seenByDownstream.get());
    }
}
