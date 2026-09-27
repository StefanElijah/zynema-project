package dev.zynema.gateway.config;

import dev.zynema.common.security.ReactiveSecuritySupport;
import dev.zynema.common.security.ZynemaSecurityPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Authorization at the edge.
 *
 * <p>The gateway is the only public entry point, so it enforces who may reach
 * which route. Downstream services validate the token again and apply their
 * own rules: a request that reaches a service directly (service-to-service,
 * or a misconfigured port mapping) is still not trusted — defence in depth,
 * not a single choke point.
 *
 * <p>Rule order matters: the admin prefix is checked before the public
 * catalogue read rule, otherwise {@code GET /api/v1/catalog/admin/**} would
 * fall into the anonymous branch at the gateway (the service would still
 * reject it, but a 403 from the edge is clearer and cheaper).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain gatewaySecurityWebFilterChain(
        ServerHttpSecurity http,
        ReactiveSecuritySupport support
    ) {
        http.authorizeExchange(exchange -> exchange
            // Operational endpoints and API docs are public (see ZynemaSecurityPaths).
            .pathMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
            .pathMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()

            // Content administration requires a management role.
            .pathMatchers("/api/v1/catalog/admin/**").hasAnyRole("content-manager", "admin")

            // Browsing the catalogue is anonymous.
            .pathMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()

            // Login-related endpoints must be reachable before a token exists.
            .pathMatchers("/api/v1/auth/public/**").permitAll()

            // Everything else (user self-service, BFF, payments, playback, ...) needs a token.
            .anyExchange().authenticated());

        support.apply(http);
        return http.build();
    }
}
