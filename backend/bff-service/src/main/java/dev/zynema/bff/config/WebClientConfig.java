package dev.zynema.bff.config;

import dev.zynema.common.webflux.CorrelationIdWebFilter;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;

/**
 * One {@link WebClient} per dependency, with the two things every outgoing
 * call needs and neither framework gives for free: the caller's token and the
 * request's correlation id.
 *
 * <p><b>Token relay.</b> The BFF never mints a token for user-initiated calls.
 * It forwards the one the caller presented, so the downstream service applies
 * its own rules to the real user (ADR-0019). A public endpoint has no
 * authentication in the Reactor context, and the call goes out anonymous —
 * which is exactly what the public catalogue reads expect.
 *
 * <p><b>Correlation and tracing.</b> {@code X-Correlation-Id} lives in the
 * Reactor context (there is no thread-local MDC in WebFlux) and is copied onto
 * every outgoing request; the W3C {@code traceparent} is propagated by the
 * Micrometer instrumentation on its own.
 *
 * <p><b>The load balancer is attached only for {@code lb://} URLs.</b> A
 * {@code @LoadBalanced} builder would resolve <em>any</em> host as a service id
 * — including the fixed {@code http://localhost:port} tests point at, which it
 * would then look up in Eureka and reject. Deciding per URL keeps production
 * (Eureka) and tests (a WireMock server) on the same code path.
 */
@Configuration
@EnableConfigurationProperties(BffProperties.class)
public class WebClientConfig {

    @Bean
    public WebClient catalogWebClient(WebClient.Builder builder, BffProperties properties,
                                      ObjectProvider<LoadBalancerClientFactory> loadBalancerFactory) {
        return build(builder, properties, properties.clients().catalog(), loadBalancerFactory);
    }

    @Bean
    public WebClient userWebClient(WebClient.Builder builder, BffProperties properties,
                                   ObjectProvider<LoadBalancerClientFactory> loadBalancerFactory) {
        return build(builder, properties, properties.clients().user(), loadBalancerFactory);
    }

    @Bean
    public WebClient paymentWebClient(WebClient.Builder builder, BffProperties properties,
                                      ObjectProvider<LoadBalancerClientFactory> loadBalancerFactory) {
        return build(builder, properties, properties.clients().payment(), loadBalancerFactory);
    }

    /** The player's session lifecycle stops being composed and becomes a pass-through. */
    @Bean
    public WebClient playbackWebClient(WebClient.Builder builder, BffProperties properties,
                                       ObjectProvider<LoadBalancerClientFactory> loadBalancerFactory) {
        return build(builder, properties, properties.clients().playback(), loadBalancerFactory);
    }

    private WebClient build(WebClient.Builder builder, BffProperties properties, String baseUrl,
                            ObjectProvider<LoadBalancerClientFactory> loadBalancerFactory) {
        Duration connectTimeout = properties.http().connectTimeout();
        Duration responseTimeout = properties.http().responseTimeout();

        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
            .responseTimeout(responseTimeout);

        WebClient.Builder outgoing = builder.clone()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .filter(correlationRelay())
            .filter(tokenRelay());

        LoadBalancerClientFactory factory = loadBalancerFactory.getIfAvailable();
        if (baseUrl.startsWith("lb://") && factory != null) {
            outgoing.filter(new ReactorLoadBalancerExchangeFilterFunction(factory, List.of()));
        }
        return outgoing.build();
    }

    /**
     * The headers the shared {@code CorrelationIdWebFilter} left on the request
     * travel to every dependency, so one id traces the whole fan-out in the
     * logs.
     */
    private ExchangeFilterFunction correlationRelay() {
        return (request, next) -> Mono.deferContextual(context -> {
            ClientRequest.Builder outgoing = ClientRequest.from(request);
            if (context.hasKey(CorrelationIdWebFilter.CONTEXT_KEY)) {
                // Typed local on purpose: header(name, values...) is varargs and
                // a bare context.get(...) would infer String[] and blow up at
                // runtime with a ClassCastException.
                String correlationId = context.get(CorrelationIdWebFilter.CONTEXT_KEY);
                outgoing.header(CorrelationIdWebFilter.HEADER, correlationId);
            }
            return next.exchange(outgoing.build());
        });
    }

    /**
     * Copies the bearer token of the current user onto the outgoing request.
     * Uses the Reactor context, not {@code @AuthenticationPrincipal}: a WebClient
     * filter runs in the caller's reactive chain, not in a controller signature.
     */
    private ExchangeFilterFunction tokenRelay() {
        return (request, next) -> ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(JwtAuthenticationToken.class::isInstance)
            .cast(JwtAuthenticationToken.class)
            .map(authentication -> authentication.getToken().getTokenValue())
            .filter(StringUtils::hasText)
            .flatMap(token -> next.exchange(authorized(request, token)))
            .switchIfEmpty(Mono.defer(() -> next.exchange(request)));
    }

    private ClientRequest authorized(ClientRequest request, String token) {
        return ClientRequest.from(request)
            .headers(headers -> headers.setBearerAuth(token))
            .build();
    }
}
