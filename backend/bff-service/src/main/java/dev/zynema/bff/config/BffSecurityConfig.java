package dev.zynema.bff.config;

import dev.zynema.common.security.ReactiveSecuritySupport;
import dev.zynema.common.security.ZynemaSecurityPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * The BFF's own authorization matrix.
 *
 * <p>It mirrors the split the product asks for, and the gateway enforces the
 * same rules at the edge (defence in depth):
 * <ul>
 *   <li><strong>Browsing is public</strong>: home and content metadata work
 *       without a token, so the catalogue is the shop window.</li>
 *   <li><strong>Watching and anything personal requires a token</strong>:
 *       account, profile rails, and everything downstream of them. The plan
 *       check ("has paid") is not here — it belongs to playback-service, which
 *       is the only place that can be trusted with it.</li>
 * </ul>
 */
@Configuration
public class BffSecurityConfig {

    @Bean
    public SecurityWebFilterChain bffSecurityWebFilterChain(
        ServerHttpSecurity http,
        ReactiveSecuritySupport support
    ) {
        http.authorizeExchange(exchange -> exchange
            .pathMatchers(ZynemaSecurityPaths.PUBLIC_OPERATIONS).permitAll()
            .pathMatchers(ZynemaSecurityPaths.PUBLIC_API_DOCS).permitAll()

            // The shop window.
            .pathMatchers(HttpMethod.GET, "/api/v1/web/home").permitAll()
            .pathMatchers(HttpMethod.GET, "/api/v1/web/catalog/**").permitAll()

            // Everything else: /account, /profiles/**, and any future write.
            .anyExchange().authenticated());

        support.apply(http);
        return http.build();
    }
}
