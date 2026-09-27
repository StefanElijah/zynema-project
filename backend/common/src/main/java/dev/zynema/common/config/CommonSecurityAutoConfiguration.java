package dev.zynema.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zynema.common.security.ApiAccessDeniedHandler;
import dev.zynema.common.security.ApiAuthenticationEntryPoint;
import dev.zynema.common.security.KeycloakRealmRoleConverter;
import dev.zynema.common.security.ServletSecuritySupport;
import dev.zynema.common.security.ZynemaJwtDecoderFactory;
import dev.zynema.common.security.ZynemaSecurityPaths;
import dev.zynema.common.security.ZynemaSecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security auto-configuration for servlet (Spring MVC) services.
 *
 * <p>Registers the shared pieces every service needs — JWT decoder, Keycloak
 * realm-role converter, JSON 401/403 handlers — and a <strong>default
 * filter chain</strong> that requires authentication for everything except
 * operational endpoints and API documentation.
 *
 * <p>The default chain is {@code @ConditionalOnMissingBean}: a service that
 * needs its own rules declares a {@link SecurityFilterChain} (composing
 * {@link ServletSecuritySupport}) and this one backs off. Services without
 * specific rules are therefore secure by default rather than open by default.
 *
 * <p>The decoder is built from a JWKS URI, not from OIDC discovery, so no
 * network call happens at startup: services still boot in the {@code core}
 * profile with Keycloak stopped (see ADR-0015).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({JwtDecoder.class, SecurityFilterChain.class})
@EnableConfigurationProperties(ZynemaSecurityProperties.class)
@EnableWebSecurity
public class CommonSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KeycloakRealmRoleConverter keycloakRealmRoleConverter() {
        return new KeycloakRealmRoleConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(ZynemaSecurityProperties properties) {
        return ZynemaJwtDecoderFactory.create(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiAuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new ApiAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiAccessDeniedHandler apiAccessDeniedHandler(ObjectMapper objectMapper) {
        return new ApiAccessDeniedHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServletSecuritySupport servletSecuritySupport(
        KeycloakRealmRoleConverter realmRoleConverter,
        ApiAuthenticationEntryPoint authenticationEntryPoint,
        ApiAccessDeniedHandler accessDeniedHandler
    ) {
        return new ServletSecuritySupport(realmRoleConverter, authenticationEntryPoint, accessDeniedHandler);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain zynemaDefaultSecurityFilterChain(
        HttpSecurity http,
        ServletSecuritySupport support
    ) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
            .requestMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()
            .anyRequest().authenticated());
        support.apply(http);
        return http.build();
    }
}
