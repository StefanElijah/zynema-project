package dev.zynema.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.feign.FeignErrorDecoder;
import dev.zynema.common.feign.FeignRequestInterceptor;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Registers the shared Feign plumbing for any servlet service that uses
 * OpenFeign.
 *
 * <p>Both beans are {@code @ConditionalOnMissingBean}, so a service can replace
 * them (for example, to add client-specific headers). The interceptor resolves
 * the tracing beans through {@link ObjectProvider} because tracing is optional.
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeignRequestInterceptor feignRequestInterceptor(
        ObjectProvider<Tracer> tracerProvider,
        ObjectProvider<Propagator> propagatorProvider
    ) {
        return new FeignRequestInterceptor(tracerProvider, propagatorProvider);
    }

    /**
     * Generic decoder used by every client: it derives the service name from
     * the request URL and unwraps the downstream error envelope. A client that
     * needs different behaviour declares its own {@code ErrorDecoder}.
     */
    @Bean
    @ConditionalOnMissingBean(name = "zynemaFeignErrorDecoder")
    public ErrorDecoder zynemaFeignErrorDecoder(ObjectMapper objectMapper) {
        return new FeignErrorDecoder(objectMapper);
    }
}
