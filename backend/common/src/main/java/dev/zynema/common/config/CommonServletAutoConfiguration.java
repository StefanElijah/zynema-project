package dev.zynema.common.config;

import dev.zynema.common.exception.GlobalExceptionHandler;
import dev.zynema.common.web.CorrelationIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration active only in servlet-based (Spring MVC) services.
 *
 * Registers:
 *  - {@link GlobalExceptionHandler}: consistent {@code ApiError} envelope for all
 *    unhandled exceptions, validation failures, and domain exceptions.
 *  - {@link CorrelationIdFilter}: propagates/generates the X-Correlation-Id header
 *    and exposes it through the SLF4J MDC so every log line carries it.
 *
 * Both are @ConditionalOnMissingBean so a service can override them locally if it
 * genuinely needs a different behavior (e.g. a service-specific filter order).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonServletAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
