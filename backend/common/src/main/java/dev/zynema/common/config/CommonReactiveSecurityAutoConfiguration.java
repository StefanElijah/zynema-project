package dev.zynema.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.security.ApiServerAccessDeniedHandler;
import dev.zynema.common.security.ApiServerAuthenticationEntryPoint;
import dev.zynema.common.security.KeycloakRealmRoleConverter;
import dev.zynema.common.security.ReactiveSecuritySupport;
import dev.zynema.common.security.ZynemaReactiveJwtDecoderFactory;
import dev.zynema.common.security.ZynemaSecurityPaths;
import dev.zynema.common.security.ZynemaSecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive counterpart of {@link CommonSecurityAutoConfiguration}.
 *
 * <p>Used by the API gateway and the BFF. Same contract: shared decoder,
 * role converter and JSON error handlers, plus a default chain that requires
 * authentication for everything except operational endpoints and API docs.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({ReactiveJwtDecoder.class, SecurityWebFilterChain.class})
@EnableConfigurationProperties(ZynemaSecurityProperties.class)
@EnableWebFluxSecurity
public class CommonReactiveSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeycloakRealmRoleConverter keycloakRealmRoleConverter() {
        return new KeycloakRealmRoleConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtDecoder reactiveJwtDecoder(ZynemaSecurityProperties properties) {
        return ZynemaReactiveJwtDecoderFactory.create(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiServerAuthenticationEntryPoint apiServerAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ApiServerAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiServerAccessDeniedHandler apiServerAccessDeniedHandler(ObjectMapper objectMapper) {
        return new ApiServerAccessDeniedHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveSecuritySupport reactiveSecuritySupport(
        KeycloakRealmRoleConverter realmRoleConverter,
        ApiServerAuthenticationEntryPoint authenticationEntryPoint,
        ApiServerAccessDeniedHandler accessDeniedHandler
    ) {
        return new ReactiveSecuritySupport(realmRoleConverter, authenticationEntryPoint, accessDeniedHandler);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
    public SecurityWebFilterChain zynemaDefaultSecurityWebFilterChain(
        ServerHttpSecurity http,
        ReactiveSecuritySupport support
    ) {
        http.authorizeExchange(exchange -> exchange
            .pathMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
            .pathMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()
            .anyExchange().authenticated());
        support.apply(http);
        return http.build();
    }
}
