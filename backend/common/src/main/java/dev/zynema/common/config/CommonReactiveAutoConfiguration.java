package dev.zynema.common.config;

import dev.zynema.common.exception.ReactiveGlobalExceptionHandler;
import dev.zynema.common.webflux.CorrelationIdWebFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration active only in reactive (WebFlux) services such as
 * bff-service and api-gateway.
 *
 * Registers the reactive counterparts of the servlet infrastructure:
 *  - {@link ReactiveGlobalExceptionHandler}: same ApiError envelope, but built
 *    from ServerWebExchange instead of servlet WebRequest.
 *  - {@link CorrelationIdWebFilter}: propagates X-Correlation-Id through the
 *    reactive chain (Reactor Context) instead of thread-bound MDC.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class CommonReactiveAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveGlobalExceptionHandler reactiveGlobalExceptionHandler() {
        return new ReactiveGlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdWebFilter correlationIdWebFilter() {
        return new CorrelationIdWebFilter();
    }
}
